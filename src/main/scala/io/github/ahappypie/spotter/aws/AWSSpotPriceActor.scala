package io.github.ahappypie.spotter.aws

import java.time.Instant

import akka.actor.{Actor, PoisonPill, Props}
import io.github.ahappypie.spotter.SpotPrice
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.Window
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{DescribeSpotPriceHistoryRequest, Ec2Exception, Filter}

import scala.jdk.CollectionConverters._

object AWSSpotPriceActor {
  def props(window: Window) = Props(new AWSSpotPriceActor(window))
}

class AWSSpotPriceActor(window: Window) extends Actor {

  override def receive: Receive = {
    case region: Region => sender ! getSpotPriceHistoryInRegion(region, window)
      self ! PoisonPill
  }

  private def getSpotPriceHistoryInRegion(region: Region, window: Window): List[SpotPrice] = {
    val ec2 = Ec2Client.builder().region(region).build()
    val filters = List(Filter.builder().name("product-description").values("Linux/UNIX").build()).asJava
//    val req = DescribeSpotPriceHistoryRequest.builder().startTime(window.start).endTime(window.end).filters(filters).build()
//    val res = ec2.describeSpotPriceHistoryPaginator(req)
    val req = DescribeSpotPriceHistoryRequest.builder().startTime(Instant.now().minusSeconds(60*60*24)).endTime(Instant.now()).instanceTypesWithStrings("r3.8xlarge").filters(filters).build()
    val res = ec2.describeSpotPriceHistoryPaginator(req)
    val data = res.spotPriceHistory().iterator().asScala.filter(p => window.contains(p.timestamp())).map(p => SpotPrice(provider = "aws", zone = p.availabilityZone(), instance = p.instanceTypeAsString(),
      timestamp = p.timestamp(), price = p.spotPrice().toDouble)).toList
    data
  }
}
