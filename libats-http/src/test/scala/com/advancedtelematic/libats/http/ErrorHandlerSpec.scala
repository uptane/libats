package com.advancedtelematic.libats.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.advancedtelematic.libats.data.ErrorRepresentation
import ErrorRepresentation._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import io.circe.CursorOp.DownField
import io.circe.DecodingFailure
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import Errors.*


class ErrorHandlerSpec extends AnyFunSuite with Matchers with ScalatestRouteTest {
  import org.apache.pekko.http.scaladsl.server.Directives._

  val routes: Route = ErrorHandler.handleErrors {
    path("error") {
      throw new RuntimeException("Some error")
    } ~
    path("ok") {
      complete("ok")
    }
  }

  test("DecodingFailure error handler keeps decoding history") {
    val errorRepresentation = Errors.decoderErrorToRepr(DecodingFailure("msg", List(DownField("field"))))
    assert(errorRepresentation.description == "DecodingFailure at .field: msg")
  }

  test("uses application/json content-type when returning error") {

    Get("/ok") ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get("/error") ~> routes ~> check {
      status shouldBe StatusCodes.InternalServerError
      responseAs[ErrorRepresentation].description shouldBe "Some error"
    }
  }
}
