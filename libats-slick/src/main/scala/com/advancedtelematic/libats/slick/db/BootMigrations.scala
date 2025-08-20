/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */

package com.advancedtelematic.libats.slick.db

import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.http.BootApp
import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

protected [db] object RunMigrations {
  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def schemaIsCompatible(dbConfig: Config): Try[Boolean] = Try {
    val f = flyway(dbConfig)
    val pendingCount = f.info().pending().length

    if(pendingCount > 0) {
      _log.error(s"$pendingCount migrations pending")
      false
    } else
      true
  }

  def apply(dbconfig: Config): Try[Int] = Try {
    _log.info("Running migrations")

    val f = flyway(dbconfig)

    val count = f.migrate()
    _log.info(s"Ran ${count.migrationsExecuted} migrations")

    count.migrationsExecuted
  }

  private def flyway(dbConfig: Config): Flyway = {
    val url = dbConfig.getString("url")
    val user = dbConfig.getString("properties.user")
    val password = dbConfig.getString("properties.password")

    val flywayConfig = Flyway.configure().dataSource(url, user, password)

    if(dbConfig.hasPath("flyway.locations")) {
      val locations = dbConfig.getStringList("flyway.locations").asScala.toList
      flywayConfig.locations(locations:_*)
    }

    if(dbConfig.hasPath("flyway.schema-table")) {
      flywayConfig.table(dbConfig.getString("flyway.schema-table"))
    }

    if (dbConfig.hasPath("flyway.clean-disabled")) {
      flywayConfig.cleanDisabled(dbConfig.getBoolean("flyway.clean-disabled"))
    }

    if (dbConfig.hasPath("flyway.placeholders")) {
      flywayConfig.placeholders(dbConfig.getObject("flyway.placeholders").asScala.view.mapValues(_.unwrapped().toString).toMap.asJava)
    }

    if (dbConfig.hasPath("catalog")) {
      flywayConfig.schemas(dbConfig.getString("catalog"))
    }

    flywayConfig.envVars()

    flywayConfig.load()
  }
}

object RunMigrationsApp {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    if(args.length != 1) {
      throw new IllegalArgumentException("Usage: RunMigrationsApp <database config path (e.g. ats.reposerver.database)>")
    }

    val dbConfig = ConfigFactory.load().getConfig(args(0))

    RunMigrations(dbConfig) match {
      case Success(l) =>
        log.info(s"$l migrations executed")
      case Failure(ex) =>
        log.error("Could not run migrations", ex)
    }
  }
}

trait CheckMigrations {
  self: BootApp =>

  val dbConfig: Config

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  if(!globalConfig.getBoolean("ats.database.skipMigrationCheck")) {
    RunMigrations.schemaIsCompatible(dbConfig) match {
      case Success(false) =>
        _log.error("Outdated migrations, terminating")
        system.terminate()
      case Success(true) =>
        _log.info("Schema is up to date")
      case Failure(ex) =>
        _log.error("Could not check schema changes compatibility", ex)
        system.terminate()
    }
  } else
    _log.info("Skipping schema compatibility check due to configuration")
}


trait BootMigrations {
  self: BootApp =>

  import system.dispatcher

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  val dbConfig: Config

  private def migrateIfEnabled: Future[Int] = {
    if (globalConfig.getBoolean("ats.database.migrate"))
      Future { FastFuture(RunMigrations(dbConfig)) }.flatten
    else
      FastFuture.successful(0)
  }

  if(globalConfig.getBoolean("ats.database.asyncMigrations"))
    migrateIfEnabled.onComplete {
      case Success(_) =>
        _log.info("Finished running migrations")
      case Failure(ex) =>
        _log.error("Could not run migrations. Fatal error, shutting down", ex)
        system.terminate()
    }
  else
    Await.result(migrateIfEnabled, Duration.Inf)
}
