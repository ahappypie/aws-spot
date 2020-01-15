package io.github.ahappypie.spotter.aws

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.Properties

import akka.actor.ActorSystem
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.Start
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.model.{DescribeSpotPriceHistoryRequest, DescribeSpotPriceHistoryResponse, Filter, SpotPrice}
import software.amazon.awssdk.services.ec2.{Ec2AsyncClient, Ec2Client}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.{Failure, Success}

object Main {

  def main(args: Array[String]): Unit = {
    val producer = getProducer()
    val regionFilters = sys.env.getOrElse("REGION_FILTERS", "us-east-1").split(",").toList
    val topic: String = sys.env.getOrElse("KAFKA_TOPIC", "spot-price-topic")
    val minute = getPreviousMinute(None)

    //val system = ActorSystem("spotter-aws")
    //system.actorOf(AWSSpotPriceSupervisor.props(regionFilters)) ! Start

    //val res = Await.result(getPrices(Region.US_EAST_1, Instant.ofEpochMilli(System.currentTimeMillis() - 60000), Instant.now()), 5.seconds)
    for(r <- regionFilters) {
      if(Region.regions().contains(Region.of(r))) {
        val history = getSpotPriceHistoryInRegion(Region.of(r), minute)
        sendRecords(producer, topic, history)
      }
    }

    producer.close()
  }

  private def getSpotPriceHistoryInRegion(region: Region, window: (Instant, Instant)): List[SpotPrice] = {
    val ec2 = Ec2Client.builder().region(region).build()
    val filters = List(Filter.builder().name("product-description").values("Linux/UNIX").build()).asJava
    val req = DescribeSpotPriceHistoryRequest.builder().startTime(window._1).endTime(window._2).filters(filters).build()
    val res = ec2.describeSpotPriceHistoryPaginator(req)
    res.spotPriceHistory().iterator().asScala.toList
  }

  private def sendRecords(producer: KafkaProducer[String, io.github.ahappypie.spotter.SpotPrice], topic: String, history: List[SpotPrice]): Unit = {
    for(p <- history) {
      val value = io.github.ahappypie.spotter.SpotPrice(provider = "aws", zone = p.availabilityZone(), instance = p.instanceTypeAsString(),
        timestamp = p.timestamp(), price = new java.lang.Double(p.spotPrice()))
      val record = new ProducerRecord[String, io.github.ahappypie.spotter.SpotPrice](topic, value)
      val future = producer.send(record)
      producer.flush()
      println(future.get().toString)
    }
  }

  private def getProducer(): KafkaProducer[String, io.github.ahappypie.spotter.SpotPrice] = {
    val props = new Properties()
    //set key serializer
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getCanonicalName)

    //set value serializer to avro serializer
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[KafkaAvroSerializer].getCanonicalName)

    //set kafka url
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env.getOrElse("KAFKA_URL", "localhost:9092"))

    //set schema registry url
    props.put("schema.registry.url", sys.env.getOrElse("SCHEMA_REGISTRY_URL", "http://localhost:8081"))

    new KafkaProducer[String, io.github.ahappypie.spotter.SpotPrice](props)
  }

  private def getPreviousMinute(t: Option[LocalDateTime]): (Instant, Instant) = {
    val now = t.getOrElse(LocalDateTime.now()).minusMinutes(1)
    val start = now.withSecond(0).withNano(0)
    val end = now.withSecond(59).withNano(999999999)
    (start.toInstant(ZoneOffset.UTC), end.toInstant(ZoneOffset.UTC))
  }
}
