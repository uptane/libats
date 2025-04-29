package com.advancedtelematic.libats.slick.db

import java.sql.Connection
import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}
import org.reactivestreams.Subscriber
import slick.jdbc.JdbcBackend.{BaseSession, JdbcDatabaseDef, Database}
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.{JdbcBackend, JdbcDataSource}
import slick.util.AsyncExecutor

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object MigrationDatabase {
  def apply(conn: Connection): MigrationDatabase = new MigrationDatabase(conn)

  private class MigrationJdbcDataSource(conn: Connection)
      extends JdbcDataSource {
    override def createConnection() = conn

    override def close() = ()

    override val maxConnections: Some[Int] = Some(1)
  }

  protected class MigrationSession(database: JdbcDatabaseDef)
      extends BaseSession(database) {
    override def close() = ()
  }

  protected class MigrationDatabase(conn: Connection)
      extends JdbcBackend.JdbcDatabaseDef(
        new MigrationJdbcDataSource(conn),
        AsyncExecutor("MigrationUmanagedDatabase-Executor", 1, -1)) {
    override def createSession(): MigrationSession = new MigrationSession(this)
  }
}

trait AppMigration extends BaseJavaMigration {

  override def getChecksum: Integer = 0

  def migrate(implicit db: Database): Future[Unit]

  override def migrate(context: Context): Unit = {
    val f = migrate(MigrationDatabase(context.getConnection))
    Await.result(f, Duration.Inf)
  }
}
