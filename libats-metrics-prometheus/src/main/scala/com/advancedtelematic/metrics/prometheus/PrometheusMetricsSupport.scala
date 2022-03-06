package com.advancedtelematic.metrics.prometheus
import java.io.StringWriter

import akka.http.scaladsl.server.Route
import com.advancedtelematic.libats.http.BootApp
import akka.http.scaladsl.server.Directives._
import com.advancedtelematic.metrics.MetricsSupport
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.exporter.common.TextFormat

object PrometheusMetricsRoutes {
  def apply(): Route = {
    (get & path("metrics")) {
      val stringWriter = new StringWriter()
      TextFormat.write004(stringWriter, CollectorRegistry.defaultRegistry.metricFamilySamples())
      complete(stringWriter.toString)
    }
  }
}

trait PrometheusMetricsSupport {
  self: BootApp with MetricsSupport =>

  lazy val prometheusMetricsRoutes: Route = {
    CollectorRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry))
    PrometheusMetricsRoutes()
  }
}
