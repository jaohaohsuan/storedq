package kb.node.storedq

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.{Config, ConfigFactory}
import kb.node.storedq.PersistenceConfigurator._
import kb.node.storedq.NodeConfigurator._

import scala.collection.JavaConversions._
/**
  * Created by henry on 4/1/16.
  */


object Main extends App {

    val config: Config = ConfigFactory.load().register().enableCassandraPlugin()

    val system = ActorSystem(config.getString("storedq.cluster-name"), config)

    system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
    system.actorOf(Props[ClusterMonitor], "cluster-monitor")

    system.actorOf(ClusterSingletonManager.props(
        singletonProps = domain.StoredQueryAggregateRoot.props(),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
    ), name = "storedQueryAggregateRoot")

    system.actorOf(ClusterSingletonProxy.props(
        singletonManagerPath = "/user/storedQueryAggregateRoot",
        settings = ClusterSingletonProxySettings(system)
    ), name = "storedQueryAggregateRootProxy")

    system.actorOf(Props[WebServer])
    system.actorOf(Props[domain.StoredQueryAggregateRootView]) ! "go"

    sys.addShutdownHook {
        system.terminate()
    }
}
