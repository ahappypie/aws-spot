package io.github.ahappypie.spotter.aws

import java.util.Properties

import akka.actor.{Actor, Props}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.ahappypie.spotter.SpotPrice
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

object KafkaPublisherActor {
  def props(topic: String) = Props(new KafkaPublisherActor(topic))
}

class KafkaPublisherActor(topic: String) extends Actor {
  var producer: KafkaProducer[String, SpotPrice] = null

  override def preStart(): Unit = {
    super.preStart()

    producer = getProducer()
  }

  override def postStop(): Unit = {
    super.postStop()

    producer.flush()
    producer.close()
    println("producer closed")
  }

  override def receive: Receive = {
    case data: List[SpotPrice] => {
      val s = data.size
      for(p <- data) {
        val f = producer.send(new ProducerRecord(topic, p))
      }
      println(s"published $s records")
      sender ! s
    }
  }

  private def getProducer(): KafkaProducer[String, SpotPrice] = {
    val props = new Properties()
    //set key serializer
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getCanonicalName)

    //set value serializer to avro serializer
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[KafkaAvroSerializer].getCanonicalName)

    //set kafka url
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env.getOrElse("KAFKA_URL", "localhost:9092"))

    //set schema registry url
    props.put("schema.registry.url", sys.env.getOrElse("SCHEMA_REGISTRY_URL", "http://localhost:8081"))

    new KafkaProducer[String, SpotPrice](props)
  }
}
