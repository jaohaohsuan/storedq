package kb.node.storedq

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import kb.node.storedq.PersistenceConfigurator._
import kb.node.storedq.NodeConfigurator._
import sample.persistence._

import scala.collection.JavaConversions._
/**
  * Created by henry on 4/1/16.
  */
object StoredqApplication {
  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.load().register().enableCassandraPlugin()

    val system = ActorSystem(config.getString("storedq.cluster-name"), config)
    system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
    system.actorOf(Props[ClusterMonitor], "cluster-monitor")

    val persistentActor = system.actorOf(Props[ExamplePersistentActor], "persistentActor-4-scala")
    persistentActor ! Cmd("foo")
    persistentActor ! Cmd("baz")
    persistentActor ! "snap"
  }
}
