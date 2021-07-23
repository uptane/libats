package com.advancedtelematic.libats.slick.db

import com.advancedtelematic.libats.test.{DatabaseSpec, MysqlDatabaseSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

class RunMigrationsSpec extends FunSuite with Matchers with ScalaFutures with MysqlDatabaseSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  lazy val flywayConfig = slickDbConfig.atKey("database")

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def beforeAll(): Unit = {
    log.warn("Not running migrations for RunMigrationsSpec")
  }

  override protected def beforeEach(): Unit = {
    cleanDatabase() // Does not drop schema, just deletes all objects
  }

  test("runs migrations") {
    RunMigrations(flywayConfig) shouldBe 1
    val sql = sql"select count(*) from schema_version".as[Int]
    db.run(sql).futureValue.head shouldBe > (0) // It will be 1 if the schema already existed, 2 if flyway created the schema
  }

  test("reports pending migrations") {
    RunMigrations.schemaIsCompatible(flywayConfig).get shouldBe false
  }

  test("runs without pending migrations") {
    cleanDatabase()
    RunMigrations(flywayConfig) shouldBe 1
    RunMigrations.schemaIsCompatible(flywayConfig).get shouldBe true
  }
}
