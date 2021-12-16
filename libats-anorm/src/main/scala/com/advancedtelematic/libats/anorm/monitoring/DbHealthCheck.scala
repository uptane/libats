package com.advancedtelematic.libats.anorm.monitoring

import anorm._
import com.advancedtelematic.metrics.HealthCheck.{Down, HealthCheckResult, Up}
import com.advancedtelematic.metrics.{HealthCheck, MetricsRepresentation}
import com.zaxxer.hikari.HikariPoolMXBean
import eu._0io.anorm_async.Database
import io.circe.Json
import io.circe.syntax._

import java.lang.management.ManagementFactory
import javax.management.{JMX, ObjectName}
import scala.concurrent.{ExecutionContext, Future}
import anorm.SqlParser._
import org.slf4j.LoggerFactory

class DbHealthMetrics()(implicit db: Database, ec: ExecutionContext) extends MetricsRepresentation {
  private lazy val mBeanServer = ManagementFactory.getPlatformMBeanServer
  private lazy val poolName = new ObjectName(s"com.zaxxer.hikari:type=Pool (${db.databaseConfig.poolName})")
  private lazy val poolProxy = JMX.newMXBeanProxy(mBeanServer, poolName, classOf[HikariPoolMXBean])

  private def dbVersion(): Future[String] = db.withConnection { implicit db =>
    SQL("SELECT VERSION()").as(scalar[String].single)
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

  def apply()(implicit ec: ExecutionContext): Future[HealthCheckResult] = db.withConnection { implicit db =>
    SQL("SELECT 1 FROM dual ").as(scalar[Int].single)
    Up
  }.recover { case ex =>
    log.error("Could not connect to db", ex)
    Down(ex)
  }

  override def name: String = "db"
}
