package com.advancedtelematic.libats.messaging.metrics

import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.messaging.ListenerMonitor
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import io.prometheus.client.{CollectorRegistry, Counter, Histogram}

import scala.concurrent.{ExecutionContext, Future}

object PrometheusMessagingMonitor {
  protected lazy val processed =
    Counter.build().name("bus_listener_processed")
      .help("bus listener processed")
      .labelNames("stream_name")
      .create().register[Counter]()

  protected lazy val error =
    Counter.build().name("bus_listener_error")
      .help("bus listener error")
      .labelNames("stream_name")
      .create().register[Counter]()

  protected lazy val restarts =
    Counter.build()
      .name("bus_listener_restarts")
      .help("bus listener restarts")
      .labelNames("stream_name")
      .create().register[Counter]()

  protected lazy val processingTime =
    Histogram.build()
      .name("bus_listener_processing_time")
      .help("bus listener processing time (seconds)")
      .labelNames("stream_name")
      .create().register[Histogram]()


  def apply[T : MessageLike]() =
    new PrometheusMessagingMonitor(implicitly[MessageLike[T]].streamName)
}

class PrometheusMessagingMonitor(streamName: String) extends ListenerMonitor {
  import PrometheusMessagingMonitor._

  override def onProcessed: Future[Unit] = FastFuture.successful(processed.labels(streamName).inc())

  override def onError(cause: Throwable): Future[Unit] = FastFuture.successful(error.labels(streamName).inc())

  override def onFinished: Future[Unit] = FastFuture.successful(restarts.labels(streamName).inc())

  override def withProcessingTimer[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val start = System.nanoTime()
    f.andThen { case _ => processingTime.labels(streamName).observe((System.nanoTime() - start) / 1e9) }
  }
}
