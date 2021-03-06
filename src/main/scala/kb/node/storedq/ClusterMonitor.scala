package kb.node.storedq

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRefFactory, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberUp, UnreachableMember}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.server.Route

/**
  * Created by henry on 4/1/16.
  */
class ClusterMonitor extends  Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberUp(member) =>
      log.info(s"Cluster member up: ${member.address}")
    case UnreachableMember(member) => log.warning(s"Cluster member unreachable: ${member.address}")
    case _: MemberEvent =>
  }
}
