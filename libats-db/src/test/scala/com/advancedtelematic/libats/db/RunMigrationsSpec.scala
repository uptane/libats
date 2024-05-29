package com.advancedtelematic.libats.db

import anorm.SQL
import anorm.SqlParser.scalar
import com.typesafe.config.ConfigFactory
import eu._0io.anorm_async.Database
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.util.Try

class RunMigrationsSpec extends AnyFunSuite with Matchers with ScalaFutures with BeforeAndAfterAll with BeforeAndAfterEach {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  private lazy val dbConfig = ConfigFactory.load().getConfig("ats.database")

  private lazy val db = Database.fromConfig(dbConfig)

  override protected def beforeEach(): Unit = {
    Try(RunMigrations.clean(dbConfig).futureValue)
  }

  test("runs migrations") {
    RunMigrations.migrate(dbConfig).get shouldBe 1

    db.withConnectionSync { implicit c =>
      import anorm.sqlToSimple
      val count = SQL("select count(*) from libats_db.flyway_schema_history").as(scalar[Int].single)
      count shouldBe > (0) // It will be 1 if the schema already existed, 2 if flyway created the schema
    }
  }

  test("reports pending migrations") {
    RunMigrations.schemaIsCompatible(dbConfig).get shouldBe false
  }

  test("runs without pending migrations") {
    RunMigrations.migrate(dbConfig).get shouldBe 1
    RunMigrations.schemaIsCompatible(dbConfig).get shouldBe true
  }
}
