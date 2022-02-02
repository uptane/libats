package com.advancedtelematic.libats.http.monitoring

import akka.actor.ActorSystem

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.Materializer
import com.advancedtelematic.metrics.HealthCheck
import com.advancedtelematic.metrics.HealthCheck.{Down, HealthCheckResult, Up}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class ServiceHealthCheck(address: Uri)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends HealthCheckHttpClient with HealthCheck {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    val req = HttpRequest(HttpMethods.GET, address.withPath(Path("/health")))
    execute(req)
      .map(_ => Up)
      .recover {
        case ex =>
          log.error(s"service $address is down", ex)
          Down(ex)
      }
  }

  override def name: String = address.toString()
}
