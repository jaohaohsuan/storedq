package kb.node.storedq

import com.typesafe.config.ConfigFactory
import NodeConfigurator._
import org.scalatest._

/**
  * Created by henry on 4/1/16.
  */
class NodeSelfRegister$Test extends FlatSpec with Matchers {
  "akka.remote.netty.tcp.port" should "be 2551" in {
    val config = ConfigFactory.load().register()
    assert(config.getInt("akka.remote.netty.tcp.port") == 2551)
  }

  "hostname" should "never use loop address" in {
    val config = ConfigFactory.load().register()
    val hostname = config.getString("akka.remote.netty.tcp.hostname")
    info(s"hostname is '$hostname'")
    List("", "127.0.0.1", "localhost") should not contain hostname
  }
}
