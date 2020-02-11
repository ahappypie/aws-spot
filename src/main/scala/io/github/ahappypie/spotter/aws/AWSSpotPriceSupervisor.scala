package io.github.ahappypie.spotter.aws


import java.time.Instant

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.util.Timeout
import io.github.ahappypie.spotter.SpotPrice
import software.amazon.awssdk.regions.Region

import scala.collection.mutable.ListBuffer
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

    case s: Int => {
      println(s"received publish of $s records")
      if(s == 0) {
        terminate()
      }
    }

    case Terminated(actor) => {
      if(priceActors.contains(actor)) {
        priceActors -= actor
        println(s"removed actor $actor from pool")
      }

    }
  }

  private def terminate(): Unit = {
    if(priceActors.isEmpty) {
      kafkaActor ! PoisonPill
      println("no more price actors, terminating...")
      context.system.terminate()
    } else {
      import context.dispatcher
      context.system.scheduler.scheduleOnce(1.second, self, 0)
    }
  }
}
