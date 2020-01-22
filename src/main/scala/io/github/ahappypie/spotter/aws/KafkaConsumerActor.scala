package io.github.ahappypie.spotter.aws

import java.util.Properties

import akka.actor.{Actor, Props}
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.github.ahappypie.spotter.SpotPrice
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringSerializer
import scala.jdk.CollectionConverters._
import java.time.Duration

object KafkaConsumerActor {
  def props(topic: String) = Props(new KafkaConsumerActor(topic))
  case class OffsetTimestampRequest()
  case class OffsetTimestampResponse(timestamp: Long)
}

class KafkaConsumerActor(topic: String) extends Actor {
  import KafkaConsumerActor._
  var consumer: KafkaConsumer[String, SpotPrice] = null

  override def preStart(): Unit = {
    super.preStart()

    consumer = getConsumer()
  }

  override def postStop(): Unit = {
    super.postStop()

    consumer.close()
    println("consumer closed")
  }

  override def receive(): Receive = {
    case OffsetTimestampRequest => {
      consumer.subscribe(List(topic).asJavaCollection)
      val records = consumer.poll(Duration.ofMillis(1000)).iterator()
      sender ! OffsetTimestampResponse(records.next().timestamp())
    }
  }

  private def getConsumer(): KafkaConsumer[String, SpotPrice] = {
    val props = new Properties()
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getCanonicalName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[KafkaAvroDeserializer].getCanonicalName)
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env.getOrElse("KAFKA_URL", "localhost:9092"))
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "aws-spot-consumer")
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

    new KafkaConsumer[String, SpotPrice](props)
  }
}
