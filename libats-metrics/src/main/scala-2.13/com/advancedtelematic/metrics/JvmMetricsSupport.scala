package com.advancedtelematic.metrics

import io.prometheus.client.hotspot.DefaultExports

trait JvmMetricsSupport {
  DefaultExports.initialize()
}
