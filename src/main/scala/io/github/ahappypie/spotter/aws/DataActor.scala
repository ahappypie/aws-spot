package io.github.ahappypie.spotter.aws

import java.util.Properties

import akka.actor.{Actor, Props}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.ahappypie.spotter.SpotPrice
import io.github.ahappypie.spotter.aws.AWSSpotPriceActor.AWSSpotPriceResponse
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

object DataActor {
  def props = Props(new DataActor)
}

class DataActor extends Actor {
  import DataActor._

  var producer: KafkaProducer[String, SpotPrice] = null

  override def preStart(): Unit = {
    super.preStart()
    val props = new Properties()
    //set key serializer
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getCanonicalName)

    //set value serializer to avro serializer
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[KafkaAvroSerializer].getCanonicalName)

    //set kafka url
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env.getOrElse("KAFKA_URL", "http://testmachine:9092"))

    //set schema registry url
    props.put("schema.registry.url", sys.env.getOrElse("SCHEMA_REGISTRY_URL", "http://testmachine:8081"))

    producer = new KafkaProducer[String, SpotPrice](props)
  }

  override def receive: Receive = {
    case data: AWSSpotPriceResponse => {
      for(p <- data.prices) {
        producer.send(new ProducerRecord("spot-price-topic",
          new SpotPrice(provider = "aws", zone = p.availabilityZone(), instance = p.instanceTypeAsString(),
            timestamp = p.timestamp(), price = p.spotPrice().toDouble)))
      }
    }
  }
}
