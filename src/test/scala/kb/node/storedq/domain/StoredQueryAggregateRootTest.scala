package kb.node.storedq.domain

import akka.actor.{ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util._

/**
  * Created by henry on 4/18/16.
  */
class StoredQueryAggregateRootTest extends TestKit(ActorSystem("testsystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    }

  "A StoredQueryAggregateRoot Actor" must {

    "send CreateStoredQuery command" in {

      val aggRoot = system.actorOf(StoredQueryAggregateRoot.props())

      var consumerId = ""
      var providerId = ""

      aggRoot ! CreateStoredQuery("query0", None, Set("test"))
      expectMsgPF() {
        case CreateStoredQueryAck(id) =>
          consumerId = id
      }

      aggRoot ! CreateStoredQuery("query1", None, Set("test"))
      expectMsgPF() {
        case CreateStoredQueryAck(id) =>
          providerId = id
      }

      consumerId should not be empty
      providerId should not be empty

      aggRoot ! AddClause(consumerId, NamedBoolClause(providerId, "query1", "must"))

      expectMsgPF() {
        case ack: AddClauseAck =>
          info("Dependencies created")
      }
    }

  }
}

