package io.github.ahappypie.spotter.aws

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import io.github.ahappypie.spotter.aws.AWSSpotPriceSupervisor.{Start, Window}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

object Main {
  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val region = sys.env.getOrElse("REGION", "us-east-1")
    val system = ActorSystem("spotter-aws")
    val supervisor = system.actorOf(AWSSpotPriceSupervisor.props(region, s"spot-price-$region"))

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
//    val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 4 msgsPer 1.second))
//    throttler ! SetTarget(Some(supervisor))
//    throttler ! Start(window)
    supervisor ! Start(window)
  }
}
