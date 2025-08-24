package com.advancedtelematic.metrics

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import org.apache.pekko.http.scaladsl.settings.{ConnectionPoolSettings, ParserSettings, RoutingSettings, ServerSettings}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Flow
import com.advancedtelematic.libats.http.BootApp
import com.codahale.metrics.{Gauge, MetricRegistry}

import scala.concurrent.ExecutionContextExecutor

trait PekkoHttpConnectionMetrics {
  self: BootApp with MetricsSupport =>

  private lazy val serverSettings = ServerSettings(system)

  metricRegistry.register("pekko.http.max-connections", new Gauge[Long]() {
    override def getValue: Long = serverSettings.maxConnections
  })

  private lazy val connectionPoolSettings = ConnectionPoolSettings(system)

  metricRegistry.register("pekko.http.host-connection-pool.max-connections", new Gauge[Long]() {
    override def getValue: Long = connectionPoolSettings.maxConnections
  })

  metricRegistry.register("pekko.http.host-connection-pool.max-open-requests", new Gauge[Long]() {
    override def getValue: Long = connectionPoolSettings.maxOpenRequests
  })

  def withConnectionMetrics(routes: Route, metricRegistry: MetricRegistry)(implicit
                                                                           routingSettings: RoutingSettings,
                                                                           parserSettings: ParserSettings,
                                                                           materializer: Materializer,
                                                                           routingLog: RoutingLog,
                                                                           _executionContext: ExecutionContextExecutor = null,
                                                                           rejectionHandler: RejectionHandler = RejectionHandler.default,
                                                                           exceptionHandler: ExceptionHandler = null,
                                                                           system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] =
    PekkoHttpConnectionMetricsRoutes(routes, metricRegistry)(
      routingSettings = implicitly,
      parserSettings = implicitly,
      materializer = implicitly,
      routingLog = implicitly,
      executionContext = _executionContext,
      rejectionHandler = implicitly,
      exceptionHandler = implicitly,
      system = implicitly)
}

object PekkoHttpConnectionMetricsRoutes {
  def apply(routes: Route, metricRegistry: MetricRegistry)(implicit
                                                           routingSettings: RoutingSettings,
                                                           parserSettings: ParserSettings,
                                                           materializer: Materializer,
                                                           routingLog: RoutingLog,
                                                           executionContext: ExecutionContextExecutor = null,
                                                           rejectionHandler: RejectionHandler = RejectionHandler.default,
                                                           exceptionHandler: ExceptionHandler = null,
                                                           system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    val handler = Route.toFlow(routes)

    Flow[HttpRequest]
      .watchTermination() {
        case (mat, completionF) =>
          metricRegistry.counter("pekko.http.connections").inc()
          metricRegistry.counter("pekko.http.connected").inc()
          completionF.onComplete(_ => metricRegistry.counter("pekko.http.connected").dec())
          mat
      }.via(handler)
  }
}
