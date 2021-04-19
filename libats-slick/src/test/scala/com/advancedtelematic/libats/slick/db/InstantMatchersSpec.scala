package com.advancedtelematic.libats.slick.db

import java.time.Instant
import com.advancedtelematic.libats.test.InstantMatchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InstantMatchersSpec extends AnyFunSuite with Matchers with InstantMatchers {
   test("be before") {
     Instant.now() shouldBe before(Instant.now().plusSeconds(30))
   }

  test("be after") {
    Instant.now() shouldBe after(Instant.now().minusSeconds(30))
  }
}
