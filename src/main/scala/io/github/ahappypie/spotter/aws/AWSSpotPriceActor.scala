package io.github.ahappypie.spotter.aws

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.contrib.throttle.TimerBasedThrottler
import akka.contrib.throttle.Throttler._

import scala.concurrent.duration._
import io.github.ahappypie.spotter.SpotPrice
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.Window
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{DescribeSpotPriceHistoryRequest, Filter}

import scala.jdk.CollectionConverters._

object AWSSpotPriceActor {
  def props(window: Window) = Props(new AWSSpotPriceActor(window))
}

class AWSSpotPriceActor(window: Window) extends Actor with ActorLogging {

  var client: Ec2Client = null
  var reg: Region = null
  var throttle: ActorRef = null
  var baseRequest: DescribeSpotPriceHistoryRequest.Builder = null
  var next = true

  override def preStart(): Unit = {
    super.preStart()
    throttle = context.actorOf(Props(classOf[TimerBasedThrottler], 10 msgsPer 1.second))
    throttle ! SetTarget(Some(self))
  }

  override def receive: Receive = {
    case region: Region => {
      client = Ec2Client.builder().region(region).build()
      reg = region
      self ! getSpotPriceHistoryRequest(region, window)
    }
    case req: DescribeSpotPriceHistoryRequest => {
      throttle ! ec2Request(req)
      if(!next) {
        log.info("poisoning myself in region = {}", reg)
        self ! PoisonPill
      }
    }
  }

  private def getSpotPriceHistoryRequest(region: Region, window: Window): DescribeSpotPriceHistoryRequest = {
    log.info("building ec2 request region = {} window start = {} window end = {}", region, window.start, window.end)
    val filters = List(Filter.builder().name("product-description").values("Linux/UNIX").build()).asJava
    baseRequest = DescribeSpotPriceHistoryRequest.builder().startTime(window.start).endTime(window.end).filters(filters)
    baseRequest.build()
  }

  private def ec2Request(req: DescribeSpotPriceHistoryRequest): Unit = {
    log.info("making ec2 request region = {} window start = {} window end = {}", reg, req.startTime(), req.endTime())
    val res = client.describeSpotPriceHistory(req)
    if(res.nextToken() == null || res.nextToken().equals("")) {
      log.info("no more tokens in region = {}", reg)
      next = false
    } else {
      self ! baseRequest.nextToken(res.nextToken()).build()
    }

    context.parent ! res.spotPriceHistory().asScala
      .filter(p => window.contains(p.timestamp()))
      .map(p => SpotPrice(provider = "aws", zone = p.availabilityZone(), instance = p.instanceTypeAsString(),
      timestamp = p.timestamp(), price = p.spotPrice().toDouble))
      .toList
  }
}
