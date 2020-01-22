package io.github.ahappypie.spotter.aws

import akka.actor.ActorSystem
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.Start

object Main {
  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val regionFilters = sys.env.getOrElse("REGION_FILTERS", "us-east-1,us-west-2").split(",").toList
    val mode = sys.env.getOrElse("OPERATING_MODE", "IMMEDIATE").toUpperCase match {case "IMMEDIATE" => OperatingMode.IMMEDIATE case "BACKFILL" => OperatingMode.BACKFILL}
    val system = ActorSystem("spotter-aws")
    system.actorOf(AWSSpotPriceSupervisor.props(regionFilters, "spot-price-topic")) ! Start(mode)
  }
}
