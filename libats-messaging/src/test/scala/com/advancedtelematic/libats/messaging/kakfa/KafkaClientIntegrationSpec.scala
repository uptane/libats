/*
 * Copyright: Copyright (C) 2015, Jaguar Land Rover
 * License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging.kakfa

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.kafka.CommitterSettings
import akka.stream.scaladsl.{Flow, Sink}
import akka.testkit.TestKit
import com.advancedtelematic.libats.messaging.kafka.{JsonDeserializerException, KafkaClient}
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.ConfigFactory
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import java.time.Instant
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._


case class KafkaSpecMessage(id: Int, payload: String)

object KafkaSpecMessage {
  implicit val messageLike = MessageLike.derive[KafkaSpecMessage](_.id.toString)
}

class KafkaClientIntegrationSpec extends TestKit(ActorSystem("KafkaClientSpec"))
  with FunSuiteLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures
  with PatienceConfiguration
  with Eventually {

  implicit val _ec = system.dispatcher

  override implicit def patienceConfig = PatienceConfig(timeout = Span(30, Seconds), interval = Span(500, Millis))

  val publisher = KafkaClient.publisher(system, system.settings.config)

  lazy val commiterSettings = CommitterSettings(ConfigFactory.load().getConfig("ats.messaging.kafka.committer"))

  test("can send an event to bus") {
    val testMsg = KafkaSpecMessage(1, Instant.now.toString)
    val f = publisher.publish(testMsg).map(_ => 0)
    f.futureValue shouldBe 0
  }

  test("can send-receive events from bus") {
    val testMsg = KafkaSpecMessage(2, Instant.now.toString)

    val flow = Flow[KafkaSpecMessage].mapAsync(1)((_: KafkaSpecMessage) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage](system.settings.config, commiterSettings, "kafka-test", flow)
    val msgFuture = source.groupedWithin(10, 5.seconds).runWith(Sink.head)

    for {
      _ <- akka.pattern.after(3.seconds, system.scheduler)(Future.successful(()))
      _ <- publisher.publish(testMsg)
    } yield ()

    msgFuture.futureValue should contain(testMsg)
  }

  test("can send-receive and commit events from bus") {
    val testMsg = KafkaSpecMessage(3, Instant.now.toString)

    val cfg = ConfigFactory.parseString(
      """
        |messaging.listener.parallelism=2
      """.stripMargin).withFallback(system.settings.config)

    val flow = Flow[KafkaSpecMessage].mapAsync(1)((_: KafkaSpecMessage) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage](cfg, commiterSettings, "kafka-test", flow)

    val msgFuture = source.runWith(Sink.head)

    for {
      _ <- akka.pattern.after(3.seconds, system.scheduler)(Future.successful(()))
      _ <- publisher.publish(testMsg)
    } yield ()

    msgFuture.futureValue should equal(testMsg)
  }

  test("returns error when json cannot be deserialized and skipJsonErrors is false") {
    val testMsgJson = Json.obj("not-valid" -> 0.asJson)

    val jsonMsgLike = new MessageLike[Json]() {
      override def streamName: String = implicitly[MessageLike[KafkaSpecMessage]].streamName // Push a bad json to the KafkaSpecMessage stream

      override def id(v: Json): String = "0L"

      override implicit val encoder: Encoder[Json] = Encoder.encodeJson
      override implicit val decoder: Decoder[Json] = Decoder.decodeJson
    }

    val cfg = ConfigFactory.parseMap(Map("ats.messaging.kafka.skipJsonErrors" -> false).asJava).withFallback(system.settings.config)

    val flow = Flow[KafkaSpecMessage].mapAsync(1)((_: KafkaSpecMessage) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage](cfg, commiterSettings, "kafka-test", flow)

    publisher.publish(testMsgJson)(implicitly, jsonMsgLike).futureValue

    eventually {
      val msgFuture = source.runWith(Sink.head)
      msgFuture.failed.futureValue shouldBe a[JsonDeserializerException]
    }
  }

  test("skips error when json cannot be deserialized") {
    val testMsgJson = Json.obj("not-valid" -> 0.asJson)

    val jsonMsgLike = new MessageLike[Json]() {
      override def streamName: String = implicitly[MessageLike[KafkaSpecMessage]].streamName // Push a bad json to the KafkaSpecMessage stream

      override def id(v: Json): String = "0L"

      override implicit val encoder: Encoder[Json] = Encoder.encodeJson
      override implicit val decoder: Decoder[Json] = Decoder.decodeJson
    }

    val cfg = system.settings.config

    val flow = Flow[KafkaSpecMessage].mapAsync(1)((_: KafkaSpecMessage) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage](cfg, commiterSettings, "kafka-test", flow)

    val testMsg = KafkaSpecMessage(5, Instant.now.toString)

    publisher.publish(testMsgJson)(implicitly, jsonMsgLike).futureValue
    publisher.publish(testMsg).futureValue

    eventually {
      val msgFuture = source.runWith(Sink.head)
      msgFuture.futureValue should equal(testMsg)
    }
  }
}
