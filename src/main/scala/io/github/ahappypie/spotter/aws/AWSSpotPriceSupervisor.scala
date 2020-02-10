package io.github.ahappypie.spotter.aws


import java.time.Instant

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import io.github.ahappypie.spotter.SpotPrice
import software.amazon.awssdk.regions.Region

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

object AWSSpotPriceSupervisor {
  def props(regions: List[String], kafkaTopic: String) = Props(new AWSSpotPriceSupervisor(regions, kafkaTopic))
  case class Start(window: Window)
  case class Window(start: Instant, end: Instant) {
    def contains(i: Instant): Boolean = {
      (i.equals(start) || i.equals(end)) || (i.isAfter(start) && i.isBefore(end))
    }
  }
}

class AWSSpotPriceSupervisor(regions: List[String], kafkaTopic: String) extends Actor {
  import AWSSpotPriceSupervisor._
  implicit val timeout: Timeout = 5.seconds

  var kafkaActor: ActorRef = null
  var priceActors: ListBuffer[ActorRef] = new ListBuffer[ActorRef]()

  override def preStart(): Unit = {
    super.preStart()
    kafkaActor = context.actorOf(KafkaPublisherActor.props(kafkaTopic), "kafka-publisher")
  }

  override def receive: Receive = {
    case Start(window) => {
      regions.filter(r => Region.regions().contains(Region.of(r))).foreach(r => {
        val priceActor = context.actorOf(AWSSpotPriceActor.props(window))
        context.watch(priceActor)
        priceActors += priceActor
        priceActor ! Region.of(r)
      })
    }

    case prices: List[SpotPrice] => {
      val f = kafkaActor ! prices
    }

    case Terminated(actor) => {
      if(priceActors.contains(actor)) {
        priceActors -= actor
      }
      if(priceActors.isEmpty) {
        //context.system.terminate()
      }
    }
  }
}
