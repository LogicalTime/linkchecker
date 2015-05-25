package info.rkuhn.linkchecker

import akka.actor.Actor
import akka.pattern.pipe
import java.util.concurrent.Executor
import akka.actor.ActorLogging
import akka.actor.Status
import scala.concurrent.ExecutionContext
import org.jsoup.Jsoup
import scala.collection.JavaConverters._

// Made just for this one job and then stopped. It makes the single web request and pipes the scala future to itself
// where it then parses the response, searching for what is of interest and returning each item to its parent as it is found.
class Getter(url: String, depth: Int) extends Actor {
  //depth = depthWeAreSearchingAt

  // The depth data is interesting here because it is not used at all for the computation.
  // It is kept with this request because it would be burdensome/messy to store this info somewhere else!
  // This seems to violate our sense of separation of concerns, why pass info that is not used at all (except as a pass through) to this processing context?
  // I guess we should imagine working on a Henry ford assembly line. Let's say we attach wheels to a car. The whole car is passed along, including tons of stuff we don't care about.
  // The important thing is that the car has a place where we can attach a wheel.
  // So a cleaner implementation would be to have this getter take something that implements the trait:
  // trait hasUrl { def url:String} However then it needs to return the same message.
  // Hmm maybe it does use the information to tell the parent what depth the links it found are at/from. "Depth url link was found at"

  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]
  def client: WebClient = AsyncWebClient

  client get url pipeTo self

  def receive = {
    case body: String =>
      for (link <- findLinks(body))
        context.parent ! Controller.Check(link, depth) // depth = "depth url link found at"
      context.stop(self)
    case _: Status.Failure => context.stop(self)
  }

  def findLinks(body: String): Iterator[String] = {
    val document = Jsoup.parse(body, url)
    val links = document.select("a[href]")
    for {
      link <- links.iterator().asScala
    } yield link.absUrl("href")
  }
}