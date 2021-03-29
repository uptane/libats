package com.advancedtelematic.libats.slick.monitoring

import java.util
import java.lang.management.ManagementFactory

import javax.management.{JMX, ObjectName}
import com.advancedtelematic.metrics.MetricsSupport
import com.codahale.metrics.{Gauge, Metric, MetricSet}
import slick.jdbc.JdbcDataSource
import slick.jdbc.hikaricp.HikariCPJdbcDataSource
import slick.util.AsyncExecutorMXBean
import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory
import java.util
import javax.management.{JMX, ObjectName}
import scala.util.control.NonFatal

trait DatabaseMetrics { self: MetricsSupport =>
  val dbSource: JdbcDataSource

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
        println(s"slick jmx mbean not ready yet, waiting for database to connect? ${ex.getMessage}")
        zero
    }
  }

  val executorMXBean = {
    val mbeanServer = ManagementFactory.getPlatformMBeanServer
    val poolName = hikariDatasource.hconf.getPoolName
    val mbean = JMX.newMXBeanProxy(mbeanServer, new ObjectName( s"slick:type=AsyncExecutor,name=$poolName"), classOf[AsyncExecutorMXBean])
    metricRegistry.register(s"slick.$poolName", new MetricSet {
      override def getMetrics: util.Map[String, Metric] = {
        Map[String, Metric](
          "threads.active" -> gauge(() => mbean.getActiveThreads, 0),
          "threads.max" -> gauge(() => mbean.getMaxThreads, 0),
          "queue.size" -> gauge(() => mbean.getQueueSize, 0),
          "queue.maxsize" -> gauge(() => mbean.getMaxQueueSize, 0)
        ).asJava
      }
    })
  }
}
