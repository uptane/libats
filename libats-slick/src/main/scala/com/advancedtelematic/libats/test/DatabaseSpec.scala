/**
* Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
* License: MPL-2.0
*/
package com.advancedtelematic.libats.test

import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, Suite}
import slick.basic.BasicProfile
import slick.jdbc.{MySQLProfile, PostgresProfile}

import java.util.TimeZone

trait MysqlDatabaseSpec extends DatabaseSpec[MySQLProfile] {
  self: Suite =>

  override implicit lazy val db = MySQLProfile.api.Database.forConfig("", slickDbConfig)
}

trait PostgresDatabaseSpec extends DatabaseSpec[PostgresProfile] {
  self: Suite =>

  override implicit lazy val db = PostgresProfile.api.Database.forConfig("", slickDbConfig)
}

trait DatabaseSpec[P <: BasicProfile] extends BeforeAndAfterAll {
  self: Suite =>

  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

  protected lazy val schemaName = {
    val catalog = testDbConfig.getString("catalog")
    val className = this.getClass.getSimpleName
    val cleanSchemaName = catalog.split(",").head
    (cleanSchemaName + "_" + className).toLowerCase
  }

  implicit lazy val db: P#Backend#Database = throw new IllegalArgumentException("Database.db must be overrriden")

  protected def testDbConfig: Config // = config.getConfig("database")

  private [libats] lazy val slickDbConfig: Config = {
    val confStr = s"""
                     | catalog = "$schemaName"
                     | properties.currentSchema = "\\\"$$user\\\", public, $schemaName"
                     |""".stripMargin

    val withSchemaName = ConfigFactory.parseString(confStr)
    withSchemaName.withFallback(testDbConfig)
  }

  protected [libats] def cleanDatabase(): Unit = {
    flyway.clean()
  }

  private lazy val flyway = {
    val url = slickDbConfig.getString("url")
    val user = slickDbConfig.getConfig("properties").getString("user")
    val password = slickDbConfig.getConfig("properties").getString("password")

    val schemaName = slickDbConfig.getString("catalog")

    Flyway.configure()
      .dataSource(url, user, password)
      .schemas(schemaName)
      .load()
  }

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
