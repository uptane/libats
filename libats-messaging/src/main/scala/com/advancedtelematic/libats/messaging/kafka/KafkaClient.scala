/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 *  License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging.kafka

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import org.apache.pekko.kafka.scaladsl.Consumer.Control
import org.apache.pekko.kafka.scaladsl.{Committer, Consumer}
import org.apache.pekko.kafka.*
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging.metrics.KafkaMetrics
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.Config
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.*

import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future, Promise}

object KafkaClient {

  def publisher(system: ActorSystem, config: Config): MessageBusPublisher = {
    val topicNameFn = topic(config)
    val kafkaProducer = producer(config)(system)

    new MessageBusPublisher {
      override def publish[T](msg: T)(
          implicit ex: ExecutionContext,
          messageLike: MessageLike[T]): Future[Unit] = {
        val promise = Promise[RecordMetadata]()

        val topic = topicNameFn(messageLike.streamName)

        val record = new ProducerRecord[Array[Byte], String](
          topic,
          messageLike.id(msg).getBytes,
          msg.asJson(messageLike.encoder).noSpaces)

        //noinspection ConvertExpressionToSAM
        kafkaProducer.send(
          record,
          new Callback {
            override def onCompletion(metadata: RecordMetadata,
                                      exception: Exception): Unit = {
              if (exception != null)
                promise.failure(exception)
              else if (metadata != null)
                promise.success(metadata)
              else
                promise.failure(
                  new Exception(
                    "Unknown error occurred, no metadata or error received"))
            }
          }
        )

        promise.future.map(_ => ())
      }
    }
  }

  def autocommitSource[T](config: Config, groupId: String, groupInstanceId: Option[String])(
      implicit ml: MessageLike[T]): Source[T, NotUsed] = {
    val (consumerSettings, subscriptions) = buildKafkaConsumer(config, groupId, groupInstanceId)
    val settings = consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    Consumer.plainSource(settings, subscriptions).map(_.value()).filter(_ != null).mapMaterializedValue(_ => NotUsed)
  }

  def committedSource[T](config: Config, committerSettings: CommitterSettings, groupIdPrefix: String, groupInstanceId: Option[String], processingFlow: Flow[CommittableMessage[Array[Byte], T], (T, CommittableOffset), NotUsed])
                        (implicit ml: MessageLike[T], system: ActorSystem): Source[T, Control] = {
    val committerSink = Committer.sink(committerSettings)

    committableSource(config, groupIdPrefix, groupInstanceId)
      .via(processingFlow)
      .alsoTo(Flow[(T, CommittableOffset)].map(_._2).to(committerSink))
      .map(_._1)
  }

  def committableSource[T](config: Config, groupIdPrefix: String, groupInstanceId: Option[String])
                          (implicit ml: MessageLike[T], system: ActorSystem): Source[CommittableMessage[Array[Byte], T], Control] = {
    val (cfgSettings, subscriptions) = buildKafkaConsumer(config, groupIdPrefix, groupInstanceId)
    val log = Logging.getLogger(system, this.getClass)

    Consumer.committableSource(cfgSettings, subscriptions)
      .filter(_.record.value() != null)
      .wireTap { msg => log.debug(s"Parsed ${msg.record.value()}") }
  }

  // groupInstanceId is used as kafka-client's group.instance.id and makes this consumer a dynamic consumer vs. static
  // See kafka docs
  private def buildKafkaConsumer[M](config: Config, groupIdPrefix: String, groupInstanceId: Option[String] = None)
                               (implicit ml: MessageLike[M]): (ConsumerSettings[Array[Byte], M], Subscription) = {
    val topicFn = topic(config)
    val consumerSettings = {
      val host = config.getString("ats.messaging.kafka.host")
      val groupId = groupIdPrefix + "-" + topicFn(ml.streamName)
      val skipJsonErrors =
        config.getBoolean("ats.messaging.kafka.skipJsonErrors")

      val consumerConfig = config.getConfig("ats.messaging.kafka.consumer")

      ConsumerSettings(consumerConfig,
                       new ByteArrayDeserializer,
                       new JsonDeserializer(ml.decoder,
                                            throwException = !skipJsonErrors))
        .withBootstrapServers(host)
        .withGroupId(groupId)
        .withClientId(s"consumer-$groupId")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        .withProperty("metric.reporters", classOf[KafkaMetrics].getName)
        .withGroupInstanceId(groupInstanceId.orNull)
    }

    val subscription = Subscriptions.topics(topicFn(ml.streamName))

    consumerSettings -> subscription
  }

  private[this] def topic(config: Config): String => String = {
    val suffix = config.getString("ats.messaging.kafka.topicSuffix")
    (streamName: String) =>
      streamName + "-" + suffix
  }

  private[this] def producer(config: Config)(
      implicit system: ActorSystem): KafkaProducer[Array[Byte], String] =
    ProducerSettings.createKafkaProducer(
      ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
        .withBootstrapServers(config.getString("ats.messaging.kafka.host"))
        .withProperty("metric.reporters", classOf[KafkaMetrics].getName)
    )
}
