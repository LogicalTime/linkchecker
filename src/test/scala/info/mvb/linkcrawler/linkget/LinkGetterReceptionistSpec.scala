package info.mvb.linkcrawler.linkget

import java.util.concurrent.Executor

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.testkit.{ImplicitSender, TestKit}
import info.mvb.linkcrawler.common.{BadStatus, WebClient}
import info.mvb.linkcrawler.linkcrawl.LinkCrawler
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Future

class StepParent(child: Props, fwd: ActorRef) extends Actor {
  context.watch(context.actorOf(child, "child"))
  def receive = {
    case Terminated(_) => context.stop(self)
    case msg           => fwd.tell(msg, sender)
  }
}

object GetterSpec {

  val firstLink = "http://www.rkuhn.info/1"

  val bodies = Map(
    firstLink ->
      """<html>
        |  <head><title>Page 1</title></head>
        |  <body>
        |    <h1>A Link</h1>
        |   <a href="http://rkuhn.info/2">click here</a>
        |  </body>
        |</html>""".stripMargin)

  val links = Map(
    firstLink -> Seq("http://rkuhn.info/2"))

  object FakeWebClient extends WebClient {
    def get(url: String)(implicit exec: Executor): Future[String] =
      bodies get url match {
        case None       => Future.failed(BadStatus(404))
        case Some(body) => Future.successful(body)
      }
  }

  def fakeGetter(url: String, depth: Int): Props =
    Props(new LinkGetterReceptionist(url, depth) {
      override def linkGetterProps: Props = LinkGetter.props(url, FakeWebClient)
//      override def client = FakeWebClient //TODO how to get fake web client into child?
    })

}

class GetterSpec extends TestKit(ActorSystem("GetterSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  import GetterSpec._

  override def afterAll(): Unit = {
    system.shutdown()
  }

  "A Getter" must {

    "return the right body" in {
      val getter = system.actorOf(Props(new StepParent(fakeGetter(firstLink, 2), testActor)), "rightBody")
      for (link <- links(firstLink))
        expectMsg(LinkCrawler.Check(link, 2))
      watch(getter)
      expectMsg(LinkGetterReceptionist.Done)
      expectTerminated(getter)
    }

    "properly finish in case of errors" in {
      val getter = system.actorOf(Props(new StepParent(fakeGetter("unknown", 2), testActor)), "wrongLink")
      watch(getter)
      expectTerminated(getter)
    }

  }

}