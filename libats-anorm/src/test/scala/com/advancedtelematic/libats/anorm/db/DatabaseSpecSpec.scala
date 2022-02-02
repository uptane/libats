package com.advancedtelematic.libats.anorm.db

import com.advancedtelematic.libats.test.DatabaseSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSuite, Matchers}
import anorm.SqlParser._
import anorm.SQL

class DatabaseSpecSpec extends FunSuite with DatabaseSpec with Matchers {

  override protected val testDbConfig: Config = ConfigFactory.load().getConfig("ats.database")

  test("runs migrations before test") {
    db.withConnectionSync { implicit _conn =>
      val c = SQL("select count(*) from flyway_schema_history").as(scalar[Int].single)
      c shouldBe 2
    }
  }
}
