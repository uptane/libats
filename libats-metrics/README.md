# libats-metrics
Sends dropwizard metrics to InfluxDB

Usage:
```scala
libraryDependencies += "com.advancedtelematic" %% "libats-metrics" % "version"
libraryDependencies += "com.advancedtelematic" %% "libats-metrics-pekko" % "version"

import com.advancedtelematic.metrics.InfluxDbMetricsReporter
import com.advancedtelematic.metrics.{ PekkoHttpMetricsSink, DropwizardMetrics, InfluxDbMetricsReporter, InfluxDbMetricsReporterSettings }
val cfg: InfluxDbMetricsReporterSettings = ???
InfluxDbMetricsReporter.start(cfg, DropwizardMetrics.registry, PekkoHttpMetricsSink.apply(cfg))
```

If `DropwizardMetrics.registry` is used, following JVM metrics are reported:
- garbage collections
- memory usage
- thread states
- CPU load (unix only)
