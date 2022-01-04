package com.advancedtelematic.libats.test

import com.advancedtelematic.libats.db.LibatsFlyway
import com.typesafe.config.{Config, ConfigFactory}
import eu._0io.anorm_async.Database
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, Suite}

import java.util.TimeZone

trait DatabaseSpec extends BeforeAndAfterAll {
  self: Suite =>

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

  protected lazy val schemaName = {
    val catalog = testDbConfig.getString("database")
    val className = this.getClass.getSimpleName
    val cleanSchemaName = catalog.split(",").head
    (cleanSchemaName + "_" + className).toLowerCase
  }

  implicit lazy val db: Database = Database.fromConfig(anormAsyncDbConfig)

  protected val testDbConfig: Config

  private [libats] lazy val anormAsyncDbConfig: Config = {
    val confStr = s"""
                     | flyway.schema = $schemaName
                     | jdbc-properties.currentSchema = "\\\"$$user\\\", public, $schemaName" # For psql jdbc drivers
                     | db-pool.properties.catalog = "$schemaName" # For mysql/maria jdbc drivers
                     |""".stripMargin

    val withSchemaName = ConfigFactory.parseString(confStr)
    withSchemaName.withFallback(testDbConfig)
  }

  protected [libats] def cleanDatabase(): Unit = {
    flyway.clean()
  }

  private lazy val flyway = LibatsFlyway(anormAsyncDbConfig)

  private def resetDatabase() = {
    flyway.clean()
    flyway.migrate()
  }

  override def beforeAll() {
    resetDatabase()
    super.beforeAll()
  }

  override def afterAll() {
    db.close()
    super.afterAll()
  }
}
