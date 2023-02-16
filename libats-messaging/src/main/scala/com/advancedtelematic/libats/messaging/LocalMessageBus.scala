/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Status}
import akka.stream.scaladsl.Source
import akka.stream.{CompletionStrategy, OverflowStrategy}
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object LocalMessageBus {

  @scala.annotation.nowarn
  def subscribe[T](system: ActorSystem, config: Config, op: MsgOperation[T])(implicit ec: ExecutionContext, m: MessageLike[T]): Source[T, NotUsed] = {
    val handlerParallelism = config.getInt("ats.messaging.listener.parallelism")

    val actorSource: Source[T, ActorRef] =  Source.actorRef(
      completionMatcher = { case Status.Success =>  CompletionStrategy.draining },
      failureMatcher = { case Status.Failure(err) =>  err },
      bufferSize = MessageBus.DEFAULT_CLIENT_BUFFER_SIZE,
      overflowStrategy = OverflowStrategy.dropTail
    )

    actorSource.mapMaterializedValue { ref =>
      system.eventStream.subscribe(ref, m.tag.runtimeClass)
      NotUsed
    }.mapAsync(handlerParallelism){ x: T => op.apply(x).map(_ => x) }
  }

  def publisher(system: ActorSystem): MessageBusPublisher = {
    new MessageBusPublisher {
      override def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit] = {
        Future.fromTry(Try(system.eventStream.publish(msg.asInstanceOf[AnyRef])))
      }
    }
  }
}
