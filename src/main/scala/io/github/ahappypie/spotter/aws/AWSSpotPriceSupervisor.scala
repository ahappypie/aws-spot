package io.github.ahappypie.spotter.aws


import java.time.Instant

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import io.github.ahappypie.spotter.SpotPrice
import software.amazon.awssdk.regions.Region

import scala.concurrent.Await
import scala.concurrent.duration._

object AWSSpotPriceSupervisor {
  def props(region: String, kafkaTopic: String) = Props(new AWSSpotPriceSupervisor(region, kafkaTopic))
  case class Start(window: Window)
  case class Window(start: Instant, end: Instant) {
    def contains(i: Instant): Boolean = {
      (i.equals(start) || i.equals(end)) || (i.isAfter(start) && i.isBefore(end))
    }
  }
}

class AWSSpotPriceSupervisor(region: String, kafkaTopic: String) extends Actor {
  import AWSSpotPriceSupervisor._
  implicit val timeout: Timeout = 5.seconds

  var awsRegion: Region = null
  var kafkaActor: ActorRef = null
  var priceActor: ActorRef = null

  override def preStart(): Unit = {
    super.preStart()
    if(Region.regions().contains(Region.of(region))) {
      awsRegion = Region.of(region)
    }
    kafkaActor = context.actorOf(KafkaPublisherActor.props(kafkaTopic), "kafka-publisher")
    context.watch(kafkaActor)
  }

  override def receive: Receive = {
    case Start(window) => {
        priceActor = context.actorOf(AWSSpotPriceActor.props(window))
        priceActor ! awsRegion
    }

    case prices: List[SpotPrice] => {
      val f = kafkaActor ! prices
      //Await.result(f, timeout.duration)
      //println("spot price actor finished")
      //kafkaActor ! PoisonPill
    }

    case s: String => println(s)

    case Terminated(kafkaActor) => context.system.terminate()
  }
}
