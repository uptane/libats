package com.advancedtelematic.libats.slick.monitoring

import java.lang.management.ManagementFactory
import java.util
import java.util.concurrent.ConcurrentHashMap
import javax.management.{JMX, ObjectName}
import com.advancedtelematic.metrics.{MetricsSupport, PerRegistry}
import com.zaxxer.hikari.metrics.prometheus.PrometheusHistogramMetricsTrackerFactory
import io.prometheus.client.{Collector, CollectorRegistry}
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.GaugeMetricFamily
import slick.jdbc.JdbcDataSource
import slick.jdbc.hikaricp.HikariCPJdbcDataSource
import slick.util.AsyncExecutorMXBean

import scala.jdk.CollectionConverters.*
import scala.util.Try

trait DatabaseMetrics { self: MetricsSupport =>
  val dbSource: JdbcDataSource

  val hikariDatasource = {
    val hkds = dbSource.asInstanceOf[HikariCPJdbcDataSource]
    hkds.ds.setMetricsTrackerFactory(new PrometheusHistogramMetricsTrackerFactory(collectorRegistry))
    hkds
  }

  SlickExecutorCollector.forRegistry(collectorRegistry).add(hikariDatasource.hconf.getPoolName)
}

private object SlickExecutorCollector {
  val forRegistry = new PerRegistry(registry => new SlickExecutorCollector().register[SlickExecutorCollector](registry))
}

private class SlickExecutorCollector extends Collector {
  private val executors = new ConcurrentHashMap[String, AsyncExecutorMXBean]()

  def add(poolName: String): Unit = {
    val name = new ObjectName(s"slick:type=AsyncExecutor,name=$poolName")
    executors.put(poolName, JMX.newMXBeanProxy(ManagementFactory.getPlatformMBeanServer, name, classOf[AsyncExecutorMXBean]))
  }

  private def gauge(name: String, help: String, value: AsyncExecutorMXBean => Int): MetricFamilySamples = {
    val family = new GaugeMetricFamily(name, help, List("pool").asJava)
    executors.forEach { (pool, executor) =>
      // slick registers the mbean on the first db connection, until then there's nothing to report
      Try(value(executor)).foreach(v => family.addMetric(List(pool).asJava, v.toDouble))
    }
    family
  }

  override def collect(): util.List[MetricFamilySamples] = List(
    gauge("slick_executor_threads_active", "Active slick executor threads", _.getActiveThreads),
    gauge("slick_executor_threads_max", "Max slick executor threads", _.getMaxThreads),
    gauge("slick_executor_queue_size", "Queued slick actions", _.getQueueSize),
    gauge("slick_executor_queue_max_size", "Max queued slick actions", _.getMaxQueueSize)
  ).asJava
}
