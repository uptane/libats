package com.advancedtelematic.libats.slick.monitoring

import java.util
import java.lang.management.ManagementFactory
import javax.management.{JMX, ObjectName}
import com.advancedtelematic.metrics.MetricsSupport
import com.codahale.metrics.{Gauge, Metric, MetricSet}
import com.zaxxer.hikari.HikariPoolMXBean
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcDataSource
import slick.jdbc.hikaricp.HikariCPJdbcDataSource
import slick.util.AsyncExecutorMXBean

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

trait DatabaseMetrics { self: MetricsSupport =>
  val dbSource: JdbcDataSource

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val hikariDatasource = {
    val hkds = dbSource.asInstanceOf[HikariCPJdbcDataSource]
    hkds.ds.setMetricRegistry(metricRegistry)
    hkds
  }

  private def gauge[A](f: () => A, zero: A): Gauge[A] = new Gauge[A] {
    override def getValue: A = try {
      f.apply()
    } catch {
      case NonFatal(ex) =>
        log.warn(s"slick jmx mbean not ready yet, waiting for first db connection to trigger mbean register? ${ex.getClass}/${ex.getMessage}")
        zero
    }
  }

  val executorMXBean = {
    val mbeanServer = ManagementFactory.getPlatformMBeanServer
    val poolName = hikariDatasource.hconf.getPoolName
    val slickBean = JMX.newMXBeanProxy(mbeanServer, new ObjectName( s"slick:type=AsyncExecutor,name=$poolName"), classOf[AsyncExecutorMXBean])
    metricRegistry.register(s"slick.$poolName", new MetricSet {
      override def getMetrics: util.Map[String, Metric] = {
        Map[String, Metric](
          "threads.active" -> gauge(() => slickBean.getActiveThreads, 0),
          "threads.max" -> gauge(() => slickBean.getMaxThreads, 0),
          "queue.size" -> gauge(() => slickBean.getQueueSize, 0),
          "queue.maxsize" -> gauge(() => slickBean.getMaxQueueSize, 0)
        ).asJava
      }
    })
  }
}
