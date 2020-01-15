package io.github.ahappypie.spotter.aws

import java.time.Instant

import akka.actor.{Actor, Props}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{DescribeSpotPriceHistoryRequest, Filter, SpotPrice}

import scala.jdk.CollectionConverters._

object AWSSpotPriceActor {
  def props(region: Region, startTime: Instant, endTime: Instant) = Props(new AWSSpotPriceActor(region, startTime, endTime))
  case class AWSSpotPriceRequest()
  case class AWSSpotPriceResponse(prices: List[SpotPrice])
}

class AWSSpotPriceActor(region: Region, startTime: Instant, endTime: Instant) extends Actor {
  import AWSSpotPriceActor._
  override def receive: Receive = {
    case AWSSpotPriceRequest => sender ! getPrices
  }

  private def getPrices(): List[SpotPrice] = {
    val ec2 = Ec2Client.builder().region(region).build()
    val filters = List(Filter.builder().name("product-description").values("Linux/UNIX").build()).asJava
    val req = DescribeSpotPriceHistoryRequest.builder().startTime(startTime).endTime(endTime).filters(filters).build()
    val res = ec2.describeSpotPriceHistoryPaginator(req)
    res.spotPriceHistory().iterator().asScala.toList
  }
}
