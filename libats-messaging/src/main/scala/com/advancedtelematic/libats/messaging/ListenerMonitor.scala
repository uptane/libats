package com.advancedtelematic.libats.messaging

import org.apache.pekko.http.scaladsl.util.FastFuture
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait ListenerMonitor {
  def onProcessed: Future[Unit]

  def onError(cause: Throwable): Future[Unit]

  def onFinished: Future[Unit]

  def withProcessingTimer[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = f
}

object LoggingListenerMonitor extends ListenerMonitor {
  private val _log = LoggerFactory.getLogger(this.getClass)

  override def onProcessed: Future[Unit] = Future.successful(_log.info("Processed it"))

  override def onError(cause: Throwable): Future[Unit] = Future.successful(_log.warn("Error it", cause))

  override def onFinished: Future[Unit] = Future.successful(_log.warn("Listener terminated"))
}

object NoOpListenerMonitor extends ListenerMonitor {
  override def onProcessed: Future[Unit] = FastFuture.successful(())

  override def onError(cause: Throwable): Future[Unit] = FastFuture.successful(())

  override def onFinished: Future[Unit] = FastFuture.successful(())
}
