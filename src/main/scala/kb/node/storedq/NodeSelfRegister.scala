package kb.node.storedq

import java.net.InetAddress

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.fusesource.scalate.TemplateEngine

/**
  * Created by henry on 4/1/16.
  */
object NodeSelfRegister {

  val getHostLocalAddress: PartialFunction[String, String] = {
    case ifac =>
      import java.net.NetworkInterface
      import scala.collection.JavaConversions._

      NetworkInterface.getNetworkInterfaces
        .find(_.getName equals ifac)
        .flatMap { interface =>
          interface.getInetAddresses.find(_.isSiteLocalAddress).map(_.getHostAddress)
        }
        .getOrElse("")
  }

  val getHostname: PartialFunction[String, String] = {
    case "" => InetAddress.getLocalHost.getHostName
  }

  def loadConfig(): Config = {

    val config = ConfigFactory.load()
    val storedqPart = config.getConfig("storedq")
    val clusterName = storedqPart.getString("cluster-name")
    val seedPort = storedqPart.getString("seed-port")
    val ifac = storedqPart.getString("ifac")
    val seedNodes = storedqPart.getString("seed-nodes")

    val host: String = getHostname.orElse(getHostLocalAddress)(ifac).trim

    val init: PartialFunction[String, Array[String]] = { case "" => Array(host) }
    val join: PartialFunction[String, Array[String]] = { case x: String => x.split(",").map(_.trim) }

    val `akka.cluster.seed-nodes` = init.orElse(join)(seedNodes).map {
      addr => s"""akka.cluster.seed-nodes += "akka.tcp://$clusterName@$addr:$seedPort""""
    }.mkString("\n")

    ConfigFactory.parseString(`akka.cluster.seed-nodes`)
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(host))
      .withFallback(config)
      .resolve()
  }
}
