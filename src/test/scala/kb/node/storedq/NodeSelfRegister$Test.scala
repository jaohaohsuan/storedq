package kb.node.storedq

import com.typesafe.config.ConfigFactory
import org.scalatest._

/**
  * Created by henry on 4/1/16.
  */
class NodeSelfRegister$Test extends FlatSpec with Matchers {
  "akka.remote.netty.tcp.port" should "be 2551" in {
    val config = NodeSelfRegister.loadConfig(ConfigFactory.load())
    assert(config.getInt("akka.remote.netty.tcp.port") == 2551)
  }

  "hostname" should "never use loop address" in {
    val config = NodeSelfRegister.loadConfig(ConfigFactory.load())
    val hostname = config.getString("akka.remote.netty.tcp.hostname")
    hostname should not be empty
    List("localhost", "127.0.0.1") should not contain hostname
  }
}
