package info.mvb.linkcrawler.linkget

import java.util.concurrent.Executor

import akka.actor._
import info.mvb.linkcrawler.linkcrawl.LinkCrawler

import scala.concurrent.ExecutionContext

// feature/function/role/responsibility of this actor
// Big Idea- Keeps depth info with it so controller doesn't have to keep track.
// Allows the more generic GetterMvB to not be tightly coupled to the controller behavior/function.
object LinkGetterReceptionist{
  def props(url: String, depth:Int): Props = Props(new LinkGetterReceptionist(url, depth))
  object Done
}

// Made just for this one link lookup job and then stopped. It makes the single web request and pipes the scala future to itself
// where it then parses the response, searching for what is of interest and returning each item to its parent as it is found.
class LinkGetterReceptionist(url: String, depth: Int) extends Actor with ActorLogging {
  import LinkGetterReceptionist._
  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]

  val child =context.watch(context.actorOf(LinkGetter.props(url)))



  def receive: Receive = {
    case LinkGetter.LinkFound( link) => context.parent ! LinkCrawler.Check(link, depth)
    case LinkGetter.Done => stop()
    case _: Status.Failure => context.stop(self) // TODO find out where this message comes from? I think it might come from the future from url getting, if so it can be removed
    case Terminated( msg) =>
//      log.warning("received Terminated in GetterReceptionistMvB. Msg={}", msg)
      //TODO think about termination strategies that are possible. This seems to be part of the contract with the controller.
      // Will the child stop itself when it receives a termination message from its children?
      context.stop(self)
  }

  def stop(): Unit ={
    context.parent ! Done
    context.stop(self)
  }


}