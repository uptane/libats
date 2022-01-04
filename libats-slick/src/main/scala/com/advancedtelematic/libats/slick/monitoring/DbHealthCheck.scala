package com.advancedtelematic.libats.slick.monitoring

import akka.stream.Materializer
import com.advancedtelematic.libats.http.HealthResource
import com.advancedtelematic.metrics.HealthCheck.{Down, HealthCheckResult, Up}
import com.advancedtelematic.metrics.{HealthCheck, MetricsRepresentation, MetricsSupport}
import com.codahale.metrics.MetricRegistry
import com.zaxxer.hikari.HikariPoolMXBean
import io.circe.Json
import io.circe.syntax._
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import java.lang.management.ManagementFactory
import javax.management.{JMX, ObjectName}
import scala.concurrent.{ExecutionContext, Future}

class DbHealthMetrics()(implicit db: Database, ec: ExecutionContext) extends MetricsRepresentation {
  private lazy val mBeanServer = ManagementFactory.getPlatformMBeanServer
  private lazy val hikariDatasource = db.source.asInstanceOf[HikariCPJdbcDataSource]
  private lazy val poolName = new ObjectName(s"com.zaxxer.hikari:type=Pool (${hikariDatasource.hconf.getPoolName})")
  private lazy val poolProxy = JMX.newMXBeanProxy(mBeanServer, poolName, classOf[HikariPoolMXBean])


  private def dbVersion(): Future[String] = {
    val query = sql"SELECT VERSION()".as[String].head
    db.run(query)
  }

  override def metricsJson: Future[Json] = {
    dbVersion().map { v =>
      Json.obj(
        "db_version" -> v.asJson,
        "idle_count" -> poolProxy.getIdleConnections.asJson,
        "active_count" -> poolProxy.getActiveConnections.asJson,
        "threads_waiting" -> poolProxy.getThreadsAwaitingConnection.asJson,
        "total_count" -> poolProxy.getTotalConnections.asJson
      )
    }
  }

  override def urlPrefix: String = "db"
}

class DbHealthCheck()(implicit db: Database, ec: ExecutionContext) extends HealthCheck {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    val query = sql"SELECT 1 FROM dual ".as[Int]
    db
      .run(query)
      .map(_ => Up)
      .recover { case ex =>
        log.error("Could not connect to db", ex)
        Down(ex)
      }
  }

  override def name: String = "db"
}

object DbHealthResource {
  def apply(versionRepr: Map[String, Any] = Map.empty,
            healthChecks: Seq[HealthCheck] = Seq.empty,
            healthMetrics: Seq[MetricsRepresentation] = Seq.empty,
            dependencies: Seq[HealthCheck] = Seq.empty,
            metricRegistry: MetricRegistry = MetricsSupport.metricRegistry)(implicit db: Database, ec: ExecutionContext) = {
    new HealthResource(versionRepr, new DbHealthCheck() +: healthChecks, new DbHealthMetrics() +: healthMetrics, dependencies, metricRegistry)
  }
}
