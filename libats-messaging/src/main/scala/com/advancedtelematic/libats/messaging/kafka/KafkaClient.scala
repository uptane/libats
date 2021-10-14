/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 *  License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging.kafka

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka._
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging.metrics.KafkaMetrics
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.Config
import io.circe.syntax._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization._

import scala.concurrent.{ExecutionContext, Future, Promise}

object KafkaClient {

  def publisher(system: ActorSystem, config: Config): MessageBusPublisher = {
    val topicNameFn = topic(config)
    val kafkaProducer = producer(config)(system)

    new MessageBusPublisher {
      override def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit] = {
        val promise = Promise[RecordMetadata]()

        val topic = topicNameFn(messageLike.streamName)

        val record = new ProducerRecord[Array[Byte], String](topic,
          messageLike.id(msg).getBytes, msg.asJson(messageLike.encoder).noSpaces)

        kafkaProducer.send(record, new Callback {
          override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
            if (exception != null)
              promise.failure(exception)
            else if (metadata != null)
              promise.success(metadata)
            else
              promise.failure(new Exception("Unknown error occurred, no metadata or error received"))
          }
        })

        promise.future.map(_ => ())
      }
    }
  }


  def source[T](system: ActorSystem, config: Config, groupId: String)
               (implicit ml: MessageLike[T]): Source[T, NotUsed] =
    plainSource(config, groupId)(ml, system).mapMaterializedValue(_ => NotUsed)

  private def plainSource[T](config: Config, groupIdPrefix: String)
                            (implicit ml: MessageLike[T], system: ActorSystem): Source[T, Control] = {
    val (consumerSettings, subscriptions) = buildSource(config, groupIdPrefix)
    val settings = consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    Consumer.plainSource(settings, subscriptions).map(_.value()).filter(_ != null)
  }

  def committableSource[T](config: Config, committerSettings: CommitterSettings, groupIdPrefix: String, processingFlow: Flow[T, Any, NotUsed])
                          (implicit ml: MessageLike[T], system: ActorSystem): Source[T, Control] = {
    val (cfgSettings, subscriptions) = buildSource(config, groupIdPrefix)
    val log = Logging.getLogger(system, this.getClass)

    val committerSink = Committer.sink(committerSettings)

    Consumer.committableSource(cfgSettings, subscriptions)
      .filter(_.record.value() != null)
      .map { msg => log.debug(s"Parsed ${msg.record.value()}") ; msg }
      .alsoTo {
        Flow[CommittableMessage[_, T]]
          .map(_.record.value())
          .via(processingFlow)
          .to(Sink.ignore)
      }
      .alsoTo {
        Flow[CommittableMessage[_, _]]
          .map(_.committableOffset)
          .to(committerSink)
      }
      .map(_.record.value())
  }

  private def buildSource[T, M](config: Config, groupIdPrefix: String)
                               (implicit system: ActorSystem, ml: MessageLike[M]): (ConsumerSettings[Array[Byte], M], Subscription) = {
    val topicFn = topic(config)
    val consumerSettings = {
      val host = config.getString("ats.messaging.kafka.host")
      val groupId = groupIdPrefix + "-" + topicFn(ml.streamName)
      val skipJsonErrors = config.getBoolean("ats.messaging.kafka.skipJsonErrors")

      ConsumerSettings(system, new ByteArrayDeserializer, new JsonDeserializer(ml.decoder, throwException = ! skipJsonErrors))
        .withBootstrapServers(host)
        .withGroupId(groupId)
        .withClientId(s"consumer-$groupId")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        .withProperty("metric.reporters", classOf[KafkaMetrics].getName)
    }

    val subscription = Subscriptions.topics(topicFn(ml.streamName))

    consumerSettings -> subscription
  }

  private[this] def topic(config: Config): String => String = {
    val suffix = config.getString("ats.messaging.kafka.topicSuffix")
    (streamName: String) => streamName + "-" + suffix
  }

  private[this] def producer(config: Config)
                            (implicit system: ActorSystem): KafkaProducer[Array[Byte], String] =
    ProducerSettings.createKafkaProducer(
      ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
        .withBootstrapServers(config.getString("ats.messaging.kafka.host"))
        .withProperty("metric.reporters", classOf[KafkaMetrics].getName)
    )
}
