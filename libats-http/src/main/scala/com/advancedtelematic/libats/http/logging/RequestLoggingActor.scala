package com.advancedtelematic.libats.http.logging

import org.apache.pekko.actor.{Actor, ActorSystem, DiagnosticActorLogging, Props, SupervisorStrategy}
import org.apache.pekko.event.Logging
import org.apache.pekko.event.Logging.MDC
import org.apache.pekko.routing.RoundRobinPool
import com.advancedtelematic.libats.http.logging.RequestLoggingActor.LogMsg
import com.typesafe.config.ConfigFactory

object RequestLoggingActor {
  case class LogMsg(formattedMsg: String, metrics: Map[String, String], level: Option[Logging.LogLevel])

  private val config = ConfigFactory.load()

  def router(level: Logging.LogLevel)(implicit system: ActorSystem): Props = {
    val restartStrategy = SupervisorStrategy.defaultStrategy
    val childCount = config.getInt("ats.http.logging.router.childCount")
    RoundRobinPool(childCount, supervisorStrategy = restartStrategy).props(props(level))
  }

  def props(level: Logging.LogLevel) = Props(new RequestLoggingActor(level))
}

class RequestLoggingActor(level: Logging.LogLevel) extends Actor with DiagnosticActorLogging {
  override def mdc(currentMessage: Any): MDC = currentMessage match {
    case LogMsg(_, metrics, _) =>
      metrics
    case _ =>
      Logging.emptyMDC
  }

  override def receive: Receive = {
    case LogMsg(formattedMsg, _, msgLevel) =>
      log.log(msgLevel.getOrElse(level), formattedMsg)
  }
}
