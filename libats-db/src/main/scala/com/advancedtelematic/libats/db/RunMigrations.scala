package com.advancedtelematic.libats.db

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.concurrent.Future

protected[libats] object RunMigrations {
  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def schemaIsCompatible(dbConfig: Config): Try[Boolean] = Try {
    val f = LibatsFlyway(dbConfig)
    val pendingCount = f.info().pending().length

    if (pendingCount > 0) {
      _log.error(s"$pendingCount migrations pending")
      false
    } else
      true
  }

  def migrate(dbconfig: Config): Try[Int] = Try {
    _log.info("Running migrations")

    val f = LibatsFlyway(dbconfig)

    val count = f.migrate()
    _log.info(s"Ran ${count.migrationsExecuted} migrations")

    count.migrationsExecuted
  }

  protected [db] def clean(dbConfig: Config): Future[Unit] = {
    val f = LibatsFlyway(dbConfig)
    Future.fromTry(Try(f.clean()).map(_ => ()))
  }
}
