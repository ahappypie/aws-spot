package io.github.ahappypie.spotter.aws


import java.time.{Instant, LocalDateTime, ZoneOffset}

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import io.github.ahappypie.spotter.SpotPrice
import software.amazon.awssdk.regions.Region

import scala.collection.mutable.{ArrayBuffer}
import scala.concurrent.Await
import scala.concurrent.duration._

object AWSSpotPriceSupervisor {
  def props(regionFilters: List[String], kafkaTopic: String) = Props(new AWSSpotPriceSupervisor(regionFilters, kafkaTopic))
  case class Start()
  case class End()
  case class Window(start: Instant, end: Instant)
}

class AWSSpotPriceSupervisor(regionFilters: List[String], kafkaTopic: String) extends Actor {
  import AWSSpotPriceSupervisor._
  implicit val timeout: Timeout = 5.seconds

  var dataActor: ActorRef = null
  val priceActors: ArrayBuffer[ActorRef] = new ArrayBuffer(regionFilters.size)

  override def preStart(): Unit = {
    super.preStart()
    dataActor = context.actorOf(DataActor.props(kafkaTopic), "kafka-publisher")
    context.watch(dataActor)
  }

  override def receive: Receive = {
    case Start => {
      val window = getPreviousMinute(None)
      for(r <- regionFilters) {
        if(Region.regions().contains(Region.of(r))) {
          val actor = context.actorOf(AWSSpotPriceActor.props(window))
          priceActors += actor
          actor ! Region.of(r)
        }
      }
    }

    case prices: Iterator[SpotPrice] => {
      val f = dataActor ? prices
      Await.result(f, timeout.duration)
      priceActors -= sender
      if(priceActors.isEmpty) {
        println("spot price actors finished")
        dataActor ! PoisonPill
      }
    }

    case Terminated(dataActor) => context.system.terminate()
  }

  private def getPreviousMinute(t: Option[LocalDateTime]): Window = {
    val now = t.getOrElse(LocalDateTime.now()).minusMinutes(1)
    val start = now.withSecond(0).withNano(0)
    val end = now.withSecond(59).withNano(999999999)
    Window(start.toInstant(ZoneOffset.UTC), end.toInstant(ZoneOffset.UTC))
  }
}
