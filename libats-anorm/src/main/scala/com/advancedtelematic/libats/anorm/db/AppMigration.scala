package com.advancedtelematic.libats.anorm.db

import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}

import java.sql.Connection
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait AppMigration extends BaseJavaMigration {

  override def getChecksum: Integer = 0

  def migrate(implicit conn: Connection): Future[Unit]

  override def migrate(context: Context): Unit = {
    val f = migrate(context.getConnection)
    Await.result(f, Duration.Inf)
  }
}
