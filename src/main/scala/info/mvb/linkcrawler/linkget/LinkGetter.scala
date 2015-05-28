package info.mvb.linkcrawler.linkget

import java.util.concurrent.Executor

import akka.actor.{Actor, Props, Status}
import akka.pattern.pipe
import info.mvb.linkcrawler.common.{AsyncWebClient, WebClient}
import org.jsoup.Jsoup

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object LinkGetter{
  case class LinkFound(url: String)
  object Done

  def props(url: String): Props = Props(new LinkGetter(url))
}

// feature/function/role/responsibility of this actor
// Big Idea- Hits url, finds links, returns them to the requester.

// Made just for this one job and then stopped. It makes the single web request and pipes the scala future to itself
// where it then parses the response, searching for what is of interest (a href tags here) and returning each item to its parent as it is found.
class LinkGetter(url: String) extends Actor {
  import LinkGetter._

  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]
  def client: WebClient = AsyncWebClient

  client get url pipeTo self

  def receive: Receive = {
    case body: String =>
      for (link <- findLinks(body))
        context.parent ! LinkFound(link)
      stop()
    case _: Status.Failure => context.stop(self) //TODO send error message so we know what happened!
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
