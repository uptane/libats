package com.advancedtelematic.metrics

import com.codahale.metrics.MetricRegistry
import io.prometheus.client.CollectorRegistry
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite

class PekkoHttpRequestMetricsSpec extends AnyFunSuite with ScalatestRouteTest {
  private def sample(name: String, method: String, status: String, extraLabels: (String, String)*): Double = {
    val (names, values) = (Seq("method" -> method, "status" -> status) ++ extraLabels).unzip
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name, names.toArray, values.toArray))
      .map(_.doubleValue).getOrElse(0.0)
  }

  test("records request duration as a prometheus histogram by method and status") {
    val route = PekkoHttpRequestMetrics.requestMetrics(new MetricRegistry) {
      path("ok") { complete("ok") } ~ path("missing") { complete(StatusCodes.NotFound) }
    }
    val okBefore = sample("http_server_request_duration_seconds_count", "GET", "200")
    val missingBefore = sample("http_server_request_duration_seconds_count", "PUT", "404")
    val infBefore = sample("http_server_request_duration_seconds_bucket", "GET", "200", "le" -> "+Inf")

    Get("/ok") ~> route ~> check { assert(status == StatusCodes.OK) }
    Get("/ok") ~> route ~> check { assert(status == StatusCodes.OK) }
    Put("/missing") ~> route ~> check { assert(status == StatusCodes.NotFound) }

    assert(sample("http_server_request_duration_seconds_count", "GET", "200") == okBefore + 2)
    assert(sample("http_server_request_duration_seconds_count", "PUT", "404") == missingBefore + 1)
    assert(sample("http_server_request_duration_seconds_bucket", "GET", "200", "le" -> "+Inf") == infBefore + 2)
    assert(sample("http_server_request_duration_seconds_sum", "GET", "200") > 0)
  }

  test("can be used by several registries without registering the histogram twice") {
    val registry = new MetricRegistry
    val route = PekkoHttpRequestMetrics.requestMetrics(registry)(complete("ok"))
    val other = PekkoHttpRequestMetrics.requestMetrics(new MetricRegistry)(complete("ok"))

    Get("/") ~> route ~> check { assert(status == StatusCodes.OK) }
    Get("/") ~> other ~> check { assert(status == StatusCodes.OK) }

    assert(registry.counter("http_requests").getCount == 1)
    assert(registry.histogram("http_app_response_time").getCount == 1)
  }
}
