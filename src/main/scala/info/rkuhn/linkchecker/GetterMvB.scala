package info.rkuhn.linkchecker

import akka.actor.{Props, Actor, ActorLogging, Status}
import akka.pattern.pipe
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import org.jsoup.Jsoup
import scala.collection.JavaConverters._

object GetterMvB{
  case class LinkFound(url: String)
  object Done

  def props(url: String): Props = Props(new GetterMvB(url))
}

// feature/function/role/responsibility of this actor
// Big Idea- Hits url, finds links, returns them to the requester.

// Made just for this one job and then stopped. It makes the single web request and pipes the scala future to itself
// where it then parses the response, searching for what is of interest (a href tags here) and returning each item to its parent as it is found.
class GetterMvB(url: String) extends Actor {
  import GetterMvB._

  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]
  def client: WebClient = AsyncWebClient

  client get url pipeTo self

  def receive: Receive = {
    case body: String =>
      for (link <- findLinks(body))
        context.parent ! LinkFound(link)
      stop()
    case _: Status.Failure => stop()
  }

  def stop(): Unit ={
    context.parent ! Done
    context.stop(self)
  }


  def findLinks(body: String): Iterator[String] = {
    val document = Jsoup.parse(body, url)
    val links = document.select("a[href]")
    for {
      link <- links.iterator().asScala
    } yield link.absUrl("href")
  }
}
