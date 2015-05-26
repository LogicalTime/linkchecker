package info.rkuhn.linkchecker

import akka.actor.Actor
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.SupervisorStrategy
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy

object Controller {
  case class Check(url: String, depth: Int)  // here depth = "depth url link was found at"
  case class Result(links: Set[String])
}

class Controller extends Actor with ActorLogging {
  import Controller._

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 5) {
    case _: Exception => SupervisorStrategy.Restart
  }

  var cache = Set.empty[String]

  context.setReceiveTimeout(10.seconds)



  def receive: Receive = {
    case Check(url, depth) => // here depth = "depth url link was found at"
      log.debug("{} checking {}", depth, url)
      if (!cache(url) && depth > 0) {
        //        context.watch(context.actorOf(Getter.props(url, depth - 1))) //depth-1 = "depth to search for url links at"
        context.watch(context.actorOf(GetterReceptionistMvB.props(url, depth - 1))) //depth-1 = "depth to search for url links at"
      }
      cache += url
    case Terminated(_) => //TODO why the heck is the termination message being used to signal "done", my intuition tells me this is baaad. Am I missing something?
      if (context.children.isEmpty)
        context.parent ! Result(cache)
    case ReceiveTimeout =>
      context.children foreach context.stop
  }

}