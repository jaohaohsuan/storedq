package kb.node.storedq

import akka.actor.{ActorSystem, Props}
import scala.collection.JavaConversions._
/**
  * Created by henry on 4/1/16.
  */
object StoredqApplication {
  def main(args: Array[String]): Unit = {
    val config = NodeSelfRegister.loadConfig()
    val system = ActorSystem(config.getString("storedq.cluster-name"), config)
    system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
    system.actorOf(Props[ClusterMonitor], "cluster-monitor")
  }
}
