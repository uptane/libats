package com.advancedtelematic.libats.messaging.metrics

import akka.actor.ActorRef
import com.advancedtelematic.libats.http.BootApp
import com.advancedtelematic.libats.messaging.MessageListenerSupport
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.MessageLike

trait MonitoredBusListenerSupport {
  self: BootApp with MessageListenerSupport =>

  def startMonitoredListener[T : MessageLike](op: MsgOperation[T],
                                              skipProcessingErrors: Boolean = false): ActorRef = {
    startListener(op, PrometheusMessagingMonitor[T](), skipProcessingErrors)
  }
}
