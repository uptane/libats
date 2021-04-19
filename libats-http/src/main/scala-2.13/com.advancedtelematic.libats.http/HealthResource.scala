/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package com.advancedtelematic.libats.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.metrics.{HealthCheck, JvmMetrics, LoggerMetrics, MetricsRepresentation, MetricsSupport}
import com.codahale.metrics.MetricRegistry
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class HealthResource(versionRepr: Map[String, Any] = Map.empty,
                     healthChecks: Seq[HealthCheck] = Seq.empty,
                     healthMetrics: Seq[MetricsRepresentation] = Seq.empty,
                     dependencies: Seq[HealthCheck] = Seq.empty,
                     metricRegistry: MetricRegistry = MetricsSupport.metricRegistry
                    )(implicit val ec: ExecutionContext) {
  import Directives._
  import HealthCheck._

  val defaultMetrics = Seq(new JvmMetrics(metricRegistry), new LoggerMetrics(metricRegistry))

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private def runHealthChecks(checks: Seq[HealthCheck], errorCode: StatusCode): Future[ToResponseMarshallable] = {
    val healthCheckResults = Future.traverse(checks) { check =>
      check()
        .map(check.name -> _)
        .recover { case ex => check.name -> Down(ex) }
    }

    import FailFastCirceSupport._

    healthCheckResults.map { results =>
      if (results.forall(_._2 == Up))
        ToResponseMarshallable(StatusCodes.OK -> Map("status" -> "OK"))
      else
        ToResponseMarshallable(errorCode -> results.toMap)
    }.recover { case t =>
      log.error("Could not run health checks", t)
      StatusCodes.ServiceUnavailable -> Map("status" -> "DOWN")
    }
  }

  def route = {
      (get & pathPrefix("health")) {
        val healthRoutes =
          pathEnd {
            val f = runHealthChecks(healthChecks, StatusCodes.ServiceUnavailable)
            complete(f)
          } ~
          path("version") {
            complete(versionRepr.view.mapValues(_.toString).toMap.asJson)
          } ~
          path("dependencies") {
            val f = runHealthChecks(dependencies, StatusCodes.BadGateway)
            complete(f)
          }

        (defaultMetrics ++ healthMetrics).foldLeft(healthRoutes) { (routes, metricSet) =>
          routes ~ path(metricSet.urlPrefix) {
            complete(metricSet.metricsJson)
          }
        }
      } ~ LoggingResource.route
  }
}
