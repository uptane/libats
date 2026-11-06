package com.advancedtelematic.libats.messaging.metrics

import io.prometheus.client.CollectorRegistry
import org.apache.kafka.common.metrics.stats.{Avg, Value}
import org.apache.kafka.common.metrics.{MetricConfig, Metrics}
import org.apache.kafka.common.utils.Time
import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters.*

class KafkaMetricsSpec extends AnyFunSuite {
  private def sample(name: String, labels: (String, String)*): Option[Double] =
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name, labels.map(_._1).toArray, labels.map(_._2).toArray))
      .map(_.doubleValue)

  test("exports kafka metrics as gauges labelled by their tags, until they are removed") {
    val metrics = new Metrics(new MetricConfig(), List[org.apache.kafka.common.metrics.MetricsReporter](new KafkaMetrics).asJava, Time.SYSTEM)
    val sensor = metrics.sensor("latency")
    sensor.add(metrics.metricName("request-latency-avg", "producer-metrics", Map("client-id" -> "producer-1").asJava), new Avg())
    sensor.record(10)
    sensor.record(20)

    assert(sample("kafka_producer_metrics_request_latency_avg", "client_id" -> "producer-1").contains(15.0))

    metrics.removeSensor("latency")

    assert(sample("kafka_producer_metrics_request_latency_avg", "client_id" -> "producer-1").isEmpty)
    metrics.close()
  }

  test("metrics of several clients are exported together") {
    val reporters = List.fill(2)(new KafkaMetrics)
    val clients = reporters.zipWithIndex.map { case (reporter, i) =>
      val metrics = new Metrics(new MetricConfig(), List[org.apache.kafka.common.metrics.MetricsReporter](reporter).asJava, Time.SYSTEM)
      val sensor = metrics.sensor("records")
      sensor.add(metrics.metricName("records-in-flight", "consumer-metrics", Map("client-id" -> s"consumer-$i").asJava), new Value())
      sensor.record(i + 1.0)
      metrics
    }

    assert(sample("kafka_consumer_metrics_records_in_flight", "client_id" -> "consumer-0").contains(1.0))
    assert(sample("kafka_consumer_metrics_records_in_flight", "client_id" -> "consumer-1").contains(2.0))
    clients.foreach(_.close())
  }
}
