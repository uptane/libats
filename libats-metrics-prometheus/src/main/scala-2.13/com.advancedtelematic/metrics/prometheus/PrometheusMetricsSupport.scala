package com.advancedtelematic.metrics.prometheus
import java.io.StringWriter

import org.apache.pekko.http.scaladsl.server.Route
import com.advancedtelematic.libats.http.BootApp
import org.apache.pekko.http.scaladsl.server.Directives._
import com.advancedtelematic.metrics.MetricsSupport
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

object PrometheusMetricsRoutes {
  // the default registry has the jvm wide metrics, served together with the app's own
  def apply(registry: CollectorRegistry = CollectorRegistry.defaultRegistry): Route = {
    (get & path("metrics")) {
      val stringWriter = new StringWriter()
      Seq(CollectorRegistry.defaultRegistry, registry).distinct.foreach { r =>
        TextFormat.write004(stringWriter, r.metricFamilySamples())
      }
      complete(stringWriter.toString)
    }
  }
}

trait PrometheusMetricsSupport {
  self: BootApp with MetricsSupport =>

  lazy val prometheusMetricsRoutes: Route = PrometheusMetricsRoutes(collectorRegistry)
}
