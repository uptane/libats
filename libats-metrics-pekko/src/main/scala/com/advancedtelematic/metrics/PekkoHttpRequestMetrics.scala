package com.advancedtelematic.metrics

import org.apache.pekko.http.scaladsl.server.Directive0
import io.prometheus.client.Histogram

private object RequestDurationHistogram {
  val histogram = new PerRegistry(registry =>
    Histogram.build()
      .name("http_request_duration_seconds")
      .help("Time until the response is ready to be sent")
      .labelNames("code", "method")
      .buckets(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
      .register(registry)
  )
}

trait PekkoHttpRequestMetrics extends MetricsSupport {
  import org.apache.pekko.http.scaladsl.server.Directives.*

  def requestMetrics: Directive0 = {
    val requestDuration = RequestDurationHistogram.histogram(collectorRegistry)

    extractRequestContext flatMap { ctx =>
      val startAt = System.nanoTime()

      mapResponse { resp =>
        requestDuration
          .labels(resp.status.intValue().toString, ctx.request.method.value.toLowerCase)
          .observe((System.nanoTime() - startAt) / 1e9)

        resp
      }
    }
  }
}

object PekkoHttpRequestMetrics extends PekkoHttpRequestMetrics
