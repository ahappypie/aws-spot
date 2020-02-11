package io.github.ahappypie.spotter.aws

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.{Start, Window}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    val regions = sys.env.getOrElse("REGIONS", "us-east-1").split(",").toList
    val kafkaTopic = sys.env.getOrElse("KAFKA_TOPIC", "spotter")
    val system = ActorSystem("spotter-aws")
    val supervisor = system.actorOf(AWSSpotPriceSupervisor.props(regions, kafkaTopic))

    var window: Window = null
    val now = LocalDateTime.now(ZoneOffset.UTC)
    if(sys.env.get("START_TIME").isDefined) {
      val endTime = sys.env.getOrElse("END_TIME", now.toString)
      window = Window(
        LocalDateTime.parse(sys.env("START_TIME")).toInstant(ZoneOffset.UTC),
        LocalDateTime.parse(endTime).toInstant(ZoneOffset.UTC)
      )
    } else if(sys.env.get("DURATION").isDefined) {
      val duration = Duration.create(sys.env("DURATION"))
      window = Window(now.minus(duration._1, ChronoUnit.valueOf(duration._2.name())).minusMinutes(1).toInstant(ZoneOffset.UTC), now.minusMinutes(1).toInstant(ZoneOffset.UTC))
    }

    println(s"window is ${window.start} to ${window.end}")
    supervisor ! Start(window)
  }
}
