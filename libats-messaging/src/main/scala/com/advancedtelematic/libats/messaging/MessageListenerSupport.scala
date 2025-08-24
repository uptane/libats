package com.advancedtelematic.libats.messaging

import org.apache.pekko.actor.ActorRef
import com.advancedtelematic.libats.boot.VersionInfo
import com.advancedtelematic.libats.http.BootApp
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging.daemon.MessageBusListenerActor.Subscribe
import com.advancedtelematic.libats.messaging_datatype.MessageLike

trait MessageListenerSupport {
  self: BootApp & VersionInfo =>

  import system.dispatcher

  def startListener[T](op: MsgOperation[T],
                       busListenerMonitor: ListenerMonitor,
                       skipProcessingErrors: Boolean = false,
                       actorNamePrefix: Option[String] = None)(
      implicit ml: MessageLike[T]): ActorRef = {
    val loggedOperation =
      if (skipProcessingErrors)
        MsgOperation.recoverFailed(op)(system.log, system.dispatcher)
      else
        MsgOperation.logFailed(op)(system.log, system.dispatcher)

    val groupId =
      if (globalConfig.hasPath("ats.messaging.groupIdPrefix"))
        globalConfig.getString("ats.messaging.groupIdPrefix")
      else
        projectName

    val ref = system.actorOf(
      MessageListener
        .props[T](globalConfig, loggedOperation, (List(groupId) ++ actorNamePrefix).mkString("-"), busListenerMonitor),
      (actorNamePrefix ++ List(ml.streamName, "listener")).mkString("-"))
    ref ! Subscribe
    ref
  }
}
