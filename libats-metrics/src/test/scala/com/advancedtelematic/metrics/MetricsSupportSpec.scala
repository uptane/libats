package com.advancedtelematic.metrics

import io.prometheus.client.CollectorRegistry
import org.scalatest.funsuite.AnyFunSuite

class MetricsSupportSpec extends AnyFunSuite {
  test("registers jvm metrics, however many times it is mixed in") {
    new MetricsSupport {}
    new MetricsSupport {}

    val heapUsed = CollectorRegistry.defaultRegistry.getSampleValue("jvm_memory_bytes_used", Array("area"), Array("heap"))
    assert(heapUsed != null && heapUsed > 0)
  }
}
