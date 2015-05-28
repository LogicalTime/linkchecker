package info.mvb.linkcrawler.linkcrawl

import akka.actor.{Actor, ActorLogging, OneForOneStrategy, ReceiveTimeout, SupervisorStrategy, Terminated}
import info.mvb.linkcrawler.linkget.LinkGetterReceptionist

import scala.concurrent.duration._

object LinkCrawler {
  case class Check(url: String, depth: Int)  // here depth = "depth url link was found at"
  case class Result(links: Set[String])
}

class LinkCrawler extends Actor with ActorLogging {
  import LinkCrawler._

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
        context.watch(context.actorOf(LinkGetterReceptionist.props(url, depth - 1))) //depth-1 = "depth to search for url links at"
      }
      cache += url
    case Terminated(_) => //TODO why the heck is the termination message being used to signal "done", my intuition tells me this is bad. Am I missing something?
      // It's used to signal done because it is guaranteed to arrive even if the child is on another machine and that machine dies.
      // Terminated signal is a unique one.
      if (context.children.isEmpty)
        context.parent ! Result(cache)
    case ReceiveTimeout =>
      context.children foreach context.stop
  }

}