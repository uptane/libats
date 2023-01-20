package com.advancedtelematic.libats.anorm.db

import com.advancedtelematic.libats.test.DatabaseSpec
import com.typesafe.config.{Config, ConfigFactory}
import anorm.SqlParser._
import anorm.SQL
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DatabaseSpecSpec extends AnyFunSuite with DatabaseSpec with Matchers {

  override protected val testDbConfig: Config = ConfigFactory.load().getConfig("ats.database")

  test("runs migrations before test") {
    db.withConnectionSync { implicit _conn =>
      val c = SQL("select count(*) from flyway_schema_history").as(scalar[Int].single)
      c shouldBe 2
    }
  }
}
