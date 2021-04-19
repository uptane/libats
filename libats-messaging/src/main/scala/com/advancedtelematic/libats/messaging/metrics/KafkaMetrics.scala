/*
 * Copyright 2016 ATS Advanced Telematic Systems GmbH
 */
package com.advancedtelematic.libats.messaging.metrics

import com.codahale.metrics.Gauge
import org.apache.kafka.common.metrics.{KafkaMetric, MetricsReporter}

import java.util

/**
  * Reports kafka metrics to dropwizard metrics.
  */
class KafkaMetrics extends MetricsReporter {
  import com.advancedtelematic.metrics.MetricsSupport.metricRegistry

  import scala.jdk.CollectionConverters._

  private[this] def metricName(m: KafkaMetric): String = {
    val mn = m.metricName()
    s"kafka_${mn.group()}.${mn.name()}.${mn.tags().asScala.map(x => s"${x._1}.${x._2}").mkString(".")}"
  }

  override def init(metrics: util.List[KafkaMetric]): Unit =
    metrics.asScala.foreach { x =>
      try {
        metricRegistry.register(metricName(x), new Gauge[Double] {
          override def getValue: Double = x.value()
        })
      } catch {
        case ex: IllegalArgumentException if ex.getMessage.contains("already exists") =>
          ()
      }
    }

  override def metricRemoval(metric: KafkaMetric): Unit = {
    metricRegistry.remove(metricName(metric))
  }

  override def close(): Unit = {}

  override def metricChange(metric: KafkaMetric): Unit = try {
    metricRegistry.register(
      metricName(metric),
      new Gauge[Double] {
        override def getValue: Double = metric.value()
      })
  } catch {
    case ex: IllegalArgumentException if ex.getMessage.contains("already exists") =>
      ()
  }

  override def configure(configs: util.Map[String, _]): Unit = {}
}
