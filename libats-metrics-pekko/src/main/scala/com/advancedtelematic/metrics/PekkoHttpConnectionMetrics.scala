package com.advancedtelematic.metrics

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RoutingLog}
import org.apache.pekko.http.scaladsl.settings.{ConnectionPoolSettings, ParserSettings, RoutingSettings, ServerSettings}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Flow
import com.advancedtelematic.libats.http.BootApp
import io.prometheus.client.{CollectorRegistry, Counter, Gauge}

import scala.concurrent.ExecutionContextExecutor

private class ConnectionMetrics(registry: CollectorRegistry) {
  val maxConnections = Gauge.build("pekko_http_max_connections", "Max server connections").register(registry)
  val poolMaxConnections = Gauge
    .build("pekko_http_host_connection_pool_max_connections", "Max connections per host of the client pool")
    .register(registry)
  val poolMaxOpenRequests = Gauge
    .build("pekko_http_host_connection_pool_max_open_requests", "Max open requests of the client pool")
    .register(registry)
  val connections = Counter.build("pekko_http_connections", "Server connections accepted").register(registry)
  val connected = Gauge.build("pekko_http_connected", "Open server connections").register(registry)
}

private object ConnectionMetrics {
  val forRegistry = new PerRegistry(new ConnectionMetrics(_))
}

trait PekkoHttpConnectionMetrics {
  self: BootApp with MetricsSupport =>

  private val connectionMetrics = ConnectionMetrics.forRegistry(collectorRegistry)
  connectionMetrics.maxConnections.set(ServerSettings(system).maxConnections)
  connectionMetrics.poolMaxConnections.set(ConnectionPoolSettings(system).maxConnections)
  connectionMetrics.poolMaxOpenRequests.set(ConnectionPoolSettings(system).maxOpenRequests)

  def withConnectionMetrics(routes: Route)(implicit
      routingSettings: RoutingSettings,
      parserSettings: ParserSettings,
      materializer: Materializer,
      routingLog: RoutingLog,
      _executionContext: ExecutionContextExecutor = null,
      rejectionHandler: RejectionHandler = RejectionHandler.default,
      exceptionHandler: ExceptionHandler = null,
      system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] =
    PekkoHttpConnectionMetricsRoutes(routes, collectorRegistry)(
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
  def apply(routes: Route, registry: CollectorRegistry = CollectorRegistry.defaultRegistry)(implicit
      routingSettings: RoutingSettings,
      parserSettings: ParserSettings,
      materializer: Materializer,
      routingLog: RoutingLog,
      executionContext: ExecutionContextExecutor = null,
      rejectionHandler: RejectionHandler = RejectionHandler.default,
      exceptionHandler: ExceptionHandler = null,
      system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    val handler = Route.toFlow(routes)
    val metrics = ConnectionMetrics.forRegistry(registry)

    Flow[HttpRequest]
      .watchTermination() {
        case (mat, completionF) =>
          metrics.connections.inc()
          metrics.connected.inc()
          completionF.onComplete(_ => metrics.connected.dec())
          mat
      }.via(handler)
  }
}
