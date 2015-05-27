package info.rkuhn.linkchecker

import akka.actor._
import akka.pattern.pipe
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import org.jsoup.Jsoup
import scala.collection.JavaConverters._

// feature/function/role/responsibility of this actor
// Big Idea- Keeps depth info with it so controller doesn't have to keep track.
// Allows the more generic GetterMvB to not be tightly coupled to the controller behavior/function.
object GetterReceptionistMvB{
  def props(url: String, depth:Int): Props = Props(new GetterReceptionistMvB(url, depth))
}

// Made just for this one link lookup job and then stopped. It makes the single web request and pipes the scala future to itself
// where it then parses the response, searching for what is of interest and returning each item to its parent as it is found.
class GetterReceptionistMvB(url: String, depth: Int) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]

  val child =context.watch(context.actorOf(GetterMvB.props(url)))



  def receive: Receive = {
    case GetterMvB.LinkFound( link) => context.parent ! Controller.Check(link, depth)
    case GetterMvB.Done =>
      context.stop(self)
    case _: Status.Failure => context.stop(self) // TODO find out where this message comes from? I think it might come from the future from url getting, if so it can be removed
    case Terminated( msg) =>
//      log.warning("received Terminated in GetterReceptionistMvB. Msg={}", msg)
      //TODO think about termination strategies that are possible. This seems to be part of the contract with the controller.
      // Will the child stop itself when it receives a termination message from its children?
      context.stop(self)
  }


}