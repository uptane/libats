package com.advancedtelematic.metrics

import io.prometheus.client.CollectorRegistry
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite

class PekkoHttpRequestMetricsSpec extends AnyFunSuite with ScalatestRouteTest {
  private def sample(name: String, code: String, method: String, extraLabels: (String, String)*): Double = {
    val (names, values) = (Seq("code" -> code, "method" -> method) ++ extraLabels).unzip
    Option(CollectorRegistry.defaultRegistry.getSampleValue(name, names.toArray, values.toArray))
      .map(_.doubleValue).getOrElse(0.0)
  }

  test("records request duration as a prometheus histogram by code and method") {
    val route = PekkoHttpRequestMetrics.requestMetrics {
      path("ok") { complete("ok") } ~ path("missing") { complete(StatusCodes.NotFound) }
    }
    val okBefore = sample("http_request_duration_seconds_count", "200", "get")
    val missingBefore = sample("http_request_duration_seconds_count", "404", "put")
    val infBefore = sample("http_request_duration_seconds_bucket", "200", "get", "le" -> "+Inf")

    Get("/ok") ~> route ~> check { assert(status == StatusCodes.OK) }
    Get("/ok") ~> route ~> check { assert(status == StatusCodes.OK) }
    Put("/missing") ~> route ~> check { assert(status == StatusCodes.NotFound) }

    assert(sample("http_request_duration_seconds_count", "200", "get") == okBefore + 2)
    assert(sample("http_request_duration_seconds_count", "404", "put") == missingBefore + 1)
    assert(sample("http_request_duration_seconds_bucket", "200", "get", "le" -> "+Inf") == infBefore + 2)
    assert(sample("http_request_duration_seconds_sum", "200", "get") > 0)
  }

  test("can be used by several routes without registering the histogram twice") {
    val before = sample("http_request_duration_seconds_count", "200", "delete")
    val route = PekkoHttpRequestMetrics.requestMetrics(complete("ok"))
    val other = PekkoHttpRequestMetrics.requestMetrics(complete("ok"))

    Delete("/") ~> route ~> check { assert(status == StatusCodes.OK) }
    Delete("/") ~> other ~> check { assert(status == StatusCodes.OK) }

    assert(sample("http_request_duration_seconds_count", "200", "delete") == before + 2)
  }

  test("records into the app's own registry, for apps sharing a jvm") {
    val registry = new CollectorRegistry()
    val app = new PekkoHttpRequestMetrics {
      override def collectorRegistry: CollectorRegistry = registry
    }
    val defaultBefore = sample("http_request_duration_seconds_count", "200", "patch")

    Patch("/") ~> app.requestMetrics(complete("ok")) ~> check { assert(status == StatusCodes.OK) }

    assert(registry.getSampleValue("http_request_duration_seconds_count", Array("code", "method"), Array("200", "patch")) == 1.0)
    assert(sample("http_request_duration_seconds_count", "200", "patch") == defaultBefore)
  }
}
