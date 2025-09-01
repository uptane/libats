package com.advancedtelematic.libats.http

import org.apache.pekko.actor.ActorSystem
import com.advancedtelematic.libats.db.RunMigrations
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object RunMigrationsApp {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    if(args.length != 1) {
      throw new IllegalArgumentException("Usage: RunMigrationsApp <database config path (e.g. ats.reposerver.database)>")
    }

    val dbConfig = ConfigFactory.load().getConfig(args(0))

    RunMigrations.migrate(dbConfig) match {
      case Success(l) =>
        log.info(s"$l migrations executed")
      case Failure(ex) =>
        log.error("Could not run migrations", ex)
    }
  }
}

trait CheckMigrations {
  val dbConfig: Config
  val globalConfig: Config
  val system: ActorSystem

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
      Future.fromTry(RunMigrations.migrate(dbConfig))
    else
      Future.successful(0)
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
