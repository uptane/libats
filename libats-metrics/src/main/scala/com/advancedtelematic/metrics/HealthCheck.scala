package com.advancedtelematic.metrics

import com.advancedtelematic.metrics.HealthCheck.HealthCheckResult
import io.circe.{Encoder, Json}
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}

object HealthCheck {
  import io.circe.syntax._

  sealed trait HealthCheckResult
  object Up extends HealthCheckResult
  case class Down(cause: Throwable) extends HealthCheckResult

  implicit val healthCheckResultEncoders = Encoder.instance[HealthCheckResult] {
    case Up => Json.obj("status" -> "up".asJson)
    case Down(cause) => Json.obj("status" -> "down".asJson, "cause" -> cause.getMessage.asJson)
  }
}

trait HealthCheck {
  def name: String

  def apply()(implicit ec: ExecutionContext): Future[HealthCheckResult]
}
