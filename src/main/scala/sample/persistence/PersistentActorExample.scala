package sample.persistence

import akka.actor._
import akka.persistence._
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConversions._

/**
  * Created by henry on 4/5/16.
  */
case class Cmd(data: String)
case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: Evt): ExampleState = copy(evt.data :: events)
  def size: Int = events.length
  override def toString: String = events.reverse.toString
}

class ExamplePersistentActor extends PersistentActor {
  val persistenceId = "sample-id-1"

  var state = ExampleState()
  def numEvents = state.size

  def updateState(evt: Evt): Unit = state = state.updated(evt)


  val receiveRecover: Receive = {
    case evt: Evt                                 => updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
  }

  val receiveCommand: Receive = {
    case Cmd(data) =>
      persist(Evt(s"$data-$numEvents"))(updateState)
    case "snap"  => saveSnapshot(state)
    case "print" => println(state)
  }
}

//object PersistentActorExample extends App {
//
//  //import kb.node.storedq.NodeSelfRegister
//
//  val config = NodeSelfRegister.loadConfig(ConfigFactory.load())
//  val system = ActorSystem(config.getString("storedq.cluster-name"), config)
//  system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
//
//  val persistentActor = system.actorOf(Props[ExamplePersistentActor], "persistentActor-4-scala")
//
////  persistentActor ! Cmd("foo")
////  persistentActor ! Cmd("baz")
////  persistentActor ! Cmd("bar")
////  persistentActor ! "snap"
////  persistentActor ! Cmd("buzz")
//  Thread.sleep(10000)
//  persistentActor ! "print"
//
//  system.terminate()
//}