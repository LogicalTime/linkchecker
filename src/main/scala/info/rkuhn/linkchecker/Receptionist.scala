package info.rkuhn.linkchecker

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.SupervisorStrategy
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.actor.Address
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout
import akka.actor.Deploy
import akka.remote.RemoteScope
import scala.util.Random
import akka.cluster.Member


// feature/function/role/responsibility of this actor
// Big Idea- Holds jobs in a queue, runs one at a time & monitors, communicates with client regarding job's success/failure/?progress?
// This is very much like a receptionist- communication liason between client's job requests and info coming about job- success/fail/progress
// AKA ClientJobLiason
// AKA ClientCommunicatorAndJobBuffererAndJobMonitor
object Receptionist {
  private case class Job(client: ActorRef, url: String)
  case class Get(url: String)
  case class Result(url: String, links: Set[String])
  case class Failed(url: String, reason: String)
}




// Broken down:
  // - Handles All Communication with client
    // - Queue requests or tell client queue full
    // - Get Successful and/or Failure results and send back to client
  // - Holds queue of requests to process where there are any requests
  // - Makes a child actor that processes the first job on the queue and returns results.
  // - Monitors the job that is running to be able to send back a failure response

// TODO What happens when this actor dies? I suppose the actor that sent it a job should watch for that?
// Perhaps we can not do better than that? If we saved the job in another actor that could die as well.
// It seems best to have the info in one place that the other actor can monitor. i'm not sure we can really protect against failure/death.
// What happens if in a distributed setting if this dies? Does the other machine get a Terminated message?
class Receptionist extends Actor {
  import Receptionist._

  def controllerProps: Props = Props[Controller]

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  var reqNo = 0

  def receive = waiting

  val waiting: Receive = {
    case Get(url) =>
      context.become(runNext(Vector(Job(sender, url))))
  }

  def running(queue: Vector[Job]): Receive = {
    case Controller.Result(links) =>
      val job = queue.head
      job.client ! Result(job.url, links)
      context.stop(context.unwatch(sender))
      context.become(runNext(queue.tail))
    case Terminated(_) =>
      val job = queue.head
      job.client ! Failed(job.url, "controller failed unexpectedly")
      context.become(runNext(queue.tail))
    case Get(url) =>
      context.become(enqueueJob(queue, Job(sender, url)))
  }

  // Function that returns the next state to be in. That's a different paradigm!!
  // TODO why does this increment reqNo, it seems like it could be called even when a request didn't come in.
  // Also are they requests enqueued? requests accepted? or all requests whether accepted or rejected?
  // Perhaps this is just so the controllers all have different names and the identifier name is misleading? I think that's it.
  def runNext(queue: Vector[Job]): Receive = {
    reqNo += 1
    if (queue.isEmpty) waiting
    else {
      val controller = context.actorOf(controllerProps, s"c$reqNo")
      context.watch(controller)
      controller ! Controller.Check(queue.head.url, 2)
      running(queue)
    }
  }

  def enqueueJob(queue: Vector[Job], job: Job): Receive = {
    if (queue.size > 3) {
      sender ! Failed(job.url, "queue overflow")
      running(queue)
    } else running(queue :+ job)
  }

}

class ClusterReceptionist extends Actor {
  import Receptionist._
  import ClusterEvent.{ MemberUp, MemberRemoved }

  val cluster = Cluster(context.system)
  cluster.subscribe(self, classOf[MemberUp])
  cluster.subscribe(self, classOf[MemberRemoved])

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  val randomGen = new Random
  def pick[A](coll: IndexedSeq[A]): A = coll(randomGen.nextInt(coll.size))

  def receive = awaitingMembers

  val awaitingMembers: Receive = {
    case Get(url) => sender ! Failed(url, "no nodes available")
    case current: ClusterEvent.CurrentClusterState =>
      val notMe = current.members.toVector map (_.address) filter (_ != cluster.selfAddress)
      if (notMe.nonEmpty) context.become(active(notMe))
    case MemberUp(member) if member.address != cluster.selfAddress =>
      context.become(active(Vector(member.address)))
  }

  def active(addresses: Vector[Address]): Receive = {
    case Get(url) if context.children.size < addresses.size =>
      val client = sender
      val address = pick(addresses)
      context.actorOf(Props(new Customer(client, url, address)))
    case Get(url) =>
      sender ! Failed(url, "too many parallel queries")
    case MemberUp(member) if member.address != cluster.selfAddress =>
      context.become(active(addresses :+ member.address))
    case MemberRemoved(member, _) =>
      val next = addresses filterNot (_ == member.address)
      if (next.isEmpty) context.become(awaitingMembers)
      else context.become(active(next))
  }
}

class Customer(client: ActorRef, url: String, node: Address) extends Actor {
  implicit val s = context.parent

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy
  val props = Props[Controller].withDeploy(Deploy(scope = RemoteScope(node)))
  val controller = context.actorOf(props, "controller")
  context.watch(controller)

  context.setReceiveTimeout(5.seconds)
  controller ! Controller.Check(url, 2)

  def receive = ({
    case ReceiveTimeout =>
      context.unwatch(controller)
      client ! Receptionist.Failed(url, "controller timed out")
    case Terminated(_) =>
      client ! Receptionist.Failed(url, "controller died")
    case Controller.Result(links) =>
      context.unwatch(controller)
      client ! Receptionist.Result(url, links)
  }: Receive) andThen (_ => context.stop(self))
}