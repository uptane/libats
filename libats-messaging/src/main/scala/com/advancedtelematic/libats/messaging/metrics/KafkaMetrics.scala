/*
 * Copyright 2016 ATS Advanced Telematic Systems GmbH
 */
package com.advancedtelematic.libats.messaging.metrics

import io.prometheus.client.Collector
import io.prometheus.client.Collector.MetricFamilySamples
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.metrics.{KafkaMetric, MetricsReporter}

import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

class KafkaMetrics extends MetricsReporter {
  override def init(metrics: util.List[KafkaMetric]): Unit = metrics.forEach(KafkaMetrics.add)

  override def metricChange(metric: KafkaMetric): Unit = KafkaMetrics.add(metric)

  override def metricRemoval(metric: KafkaMetric): Unit = KafkaMetrics.remove(metric)

  override def close(): Unit = {}

  override def configure(configs: util.Map[String, ?]): Unit = {}
}

object KafkaMetrics extends Collector {
  private val metrics = new ConcurrentHashMap[MetricName, KafkaMetric]()

  private lazy val registered: Unit = register[Collector]()

  private def add(metric: KafkaMetric): Unit = {
    registered
    metrics.put(metric.metricName(), metric)
  }

  private def remove(metric: KafkaMetric): Unit = metrics.remove(metric.metricName())

  private def sanitize(name: String): String = name.replaceAll("[^a-zA-Z0-9_]", "_")

  override def collect(): util.List[MetricFamilySamples] =
    metrics.values().asScala.toList
      .flatMap { metric =>
        // some metrics, like the client version, aren't numbers
        metric.metricValue() match {
          case value: java.lang.Number => Some(metric.metricName() -> value.doubleValue())
          case _ => None
        }
      }
      .groupBy { case (name, _) => s"kafka_${sanitize(name.group())}_${sanitize(name.name())}" }
      .map { case (familyName, values) =>
        val samples = values.map { case (name, value) =>
          val tags = name.tags().asScala.toList.sortBy(_._1)
          new MetricFamilySamples.Sample(familyName, tags.map(t => sanitize(t._1)).asJava, tags.map(_._2).asJava, value)
        }
        new MetricFamilySamples(familyName, Collector.Type.GAUGE, values.head._1.description(), samples.asJava)
      }
      .toList
      .asJava
}
