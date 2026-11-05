package com.advancedtelematic.metrics

import org.apache.pekko.http.scaladsl.server.Directive0
import com.codahale.metrics.MetricRegistry
import io.prometheus.client.Histogram

private object RequestDurationHistogram {
  lazy val histogram: Histogram = Histogram.build()
    .name("http_server_request_duration_seconds")
    .help("Time until the response is ready to be sent")
    .labelNames("method", "status")
    .buckets(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
    .register()
}

trait PekkoHttpRequestMetrics {
  import org.apache.pekko.http.scaladsl.server.Directives.*

  def requestMetrics(registry: MetricRegistry): Directive0 = {
    val requests = registry.counter("http_requests")
    val success = registry.counter("http_requests_success")
    val failures_4xx = registry.counter("http_requests_failed_4xx")
    val failures_5xx = registry.counter("http_requests_failed_5xx")
    val responseTime = registry.histogram("http_app_response_time")
    val requestDuration = RequestDurationHistogram.histogram

    extractRequestContext flatMap { ctx =>
      val startAt = System.nanoTime()

      mapResponse { resp =>
        requests.inc()

        if(resp.status.isSuccess())
          success.inc()
        else if(resp.status.intValue() >= 500)
          failures_5xx.inc()
        else
          failures_4xx.inc()

        val elapsedNanos = System.nanoTime() - startAt
        responseTime.update(elapsedNanos / 1000000)
        requestDuration
          .labels(ctx.request.method.value, resp.status.intValue().toString)
          .observe(elapsedNanos / 1e9)

        resp
      }
    }
  }
}

object PekkoHttpRequestMetrics extends PekkoHttpRequestMetrics
