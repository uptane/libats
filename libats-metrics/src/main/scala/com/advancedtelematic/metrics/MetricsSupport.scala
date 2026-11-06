/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 *  License: MPL-2.0
 */

package com.advancedtelematic.metrics

import io.prometheus.client.CollectorRegistry

import java.util.concurrent.ConcurrentHashMap

trait MetricsSupport extends JvmMetricsSupport {
  def collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
}

class PerRegistry[T](create: CollectorRegistry => T) {
  private val instances = new ConcurrentHashMap[CollectorRegistry, T]()

  def apply(registry: CollectorRegistry): T = instances.computeIfAbsent(registry, r => create(r))
}
