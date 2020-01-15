package io.github.ahappypie.spotter.aws

import java.time.Instant

import akka.actor.{Actor, ActorRef, Props}
import io.github.ahappypie.spotter.aws.AWSSpotPriceActor.{AWSSpotPriceRequest, AWSSpotPriceResponse}
import software.amazon.awssdk.regions.Region

import scala.jdk.CollectionConverters._

object AWSSpotPriceSupervisor {
  def props(regionFilters: List[String]) = Props(new AWSSpotPriceSupervisor(regionFilters))
  case class Start()
}

class AWSSpotPriceSupervisor(regionFilters: List[String]) extends Actor {
  import AWSSpotPriceSupervisor._
  var regions: List[Region] = null

  override def preStart(): Unit = {
    super.preStart()
    regions = Region.regions().asScala.filter(r => !r.isGlobalRegion).filter(r => regionFilters.contains(r.id())).toList
  }

  override def receive: Receive = {
    case Start => for(r <- regions) {
          context.actorOf(AWSSpotPriceActor.props(r, Instant.ofEpochMilli(System.currentTimeMillis() - 60000), Instant.now())) ! AWSSpotPriceRequest
        }
    case prices: AWSSpotPriceResponse => context.actorOf(DataActor.props) ! prices
  }
}
