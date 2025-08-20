package com.advancedtelematic.libats.messaging

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.CommitterSettings
import org.apache.pekko.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging.kafka.KafkaClient
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait MessageBusPublisher {
  lazy private val logger = LoggerFactory.getLogger(this.getClass)

  def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit]

  def publishSafe[T](msg: T)(implicit ec: ExecutionContext, messageLike: MessageLike[T]): Future[Try[Unit]] = {
    publish(msg)
      .map { _ =>
        logger.info(s"published ${messageLike.streamName} - ${messageLike.id(msg)}")
        Success(())
      }
      .recover { case t =>
        logger.error(s"Could not publish $msg msg to bus", t)
        Failure(t)
    }
  }
}

object MessageBusPublisher {
  def ignore = new MessageBusPublisher {
    lazy private val _logger = LoggerFactory.getLogger(this.getClass)

    override def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit] = {
      _logger.info(s"Ignoring message publish to bus")
      Future.successful(())
    }
  }

  implicit class FuturePipeToBus[T](v: Future[T]) {
    def pipeToBus[M](messageBusPublisher: MessageBusPublisher)
                    (fn: T => M)(implicit ec: ExecutionContext, messageLike: MessageLike[M]): Future[T] = {
      v.andThen {
        case Success(futureResult) => messageBusPublisher.publish(fn(futureResult))
      }
    }
  }
}

object MessageBus {
  val DEFAULT_CLIENT_BUFFER_SIZE = 1024 // number of msgs

  lazy val log = LoggerFactory.getLogger(this.getClass)

  def subscribe[T](system: ActorSystem, config: Config, groupId: String, op: MsgOperation[T])
                  (implicit messageLike: MessageLike[T], ec: ExecutionContext): Source[T, NotUsed] = {
    config.getString("ats.messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        log.info(s"Using stream name: ${messageLike.streamName}")
        KafkaClient.autocommitSource(config, groupId, groupInstanceId = None)(messageLike)
      case "local" | "test" =>
        log.info("Using local event bus")
        val flow = Flow[T].mapAsync(10) { msg => op(msg).map(_ => msg) }
        LocalMessageBus.subscribe(system, flow)
      case mode =>
        throw new Missing(s"Unknown messaging mode specified ($mode)")
    }
  }

  def subscribeCommittable[T](config: Config, groupIdPrefix: String, op: MsgOperation[T])
                             (implicit messageLike: MessageLike[T], ec: ExecutionContext, system: ActorSystem): Source[T, NotUsed] = {
    val listenerParallelism = config.getInt("ats.messaging.listener.parallelism")
    val processingFlow = Flow[CommittableMessage[Array[Byte], T]].mapAsync[(T, CommittableOffset)](listenerParallelism) { msg =>
      op(msg.record.value()).map(_ => msg.record.value() -> msg.committableOffset)
    }

    config.getString("ats.messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        log.info(s"Using stream name: ${messageLike.streamName}")

        val committerSettings = CommitterSettings(config.getConfig("ats.messaging.kafka.committer"))

        KafkaClient.committedSource[T](config, committerSettings, groupIdPrefix, groupInstanceId = None, processingFlow).mapMaterializedValue(_ => NotUsed)

      case "local" | "test" =>
        log.info("Using local event bus")
        val flow = processingFlow.map(_._1).contramap((value: T) => CommittableMessage(new ConsumerRecord[Array[Byte], T](messageLike.streamName, 0, 0, null, value), null))
        LocalMessageBus.subscribe(system, flow)

      case mode =>
        throw new Missing(s"Unknown messaging mode specified ($mode)")
    }
  }

  def publisher(system: ActorSystem, config: Config): MessageBusPublisher = {
    config.getString("ats.messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        KafkaClient.publisher(system, config)
      case "local" | "test" =>
        log.info("Using local message bus")
        LocalMessageBus.publisher(system)
      case mode =>
        throw new Missing(s"Unknown messaging mode specified ($mode)")
    }
  }
}
