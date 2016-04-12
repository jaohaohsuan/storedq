package kb.node.storedq

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, SnapshotOffer}
import scala.util.{Failure, Success, Try}

/**
  * Created by henry on 4/8/16.
  */
trait Event
trait State
trait Command
trait Acd

trait BoolClause {
  val occur: String
}
case class MatchBoolClause(query: String, field: String, operator: String, occur: String) extends BoolClause
case class NamedBoolClause(storedQueryId: String, title: String, occur: String, clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

// commands
case object Initial extends Command
case class CreateNewStoredQuery(title: String, referredId: Option[String], tags: Set[String]) extends Command
case class AddClause(storedQueryId: String, clause: BoolClause) extends Command

// events
case class ItemCreated(entity: StoredQuery, dependencies: Map[(String, String), Int]) extends Event
case class ItemsChanged(items: Seq[(String, StoredQuery)], changes: List[String], dependencies: Map[(String, String), Int]) extends Event

// state
case class StoredQueries(items: Map[String, StoredQuery] = Map.empty,
                         dependencies: Map[(String, String), Int] = Map.empty, changes: Map[String, Int] = Map.empty) extends State

case class CreatedAck(id: String)

object StoredQueryAggregateRoot {

  type Dependencies = Map[(String, String), Int]

  val Id = "stored-query-aggregate-root"

  implicit class StoredQueryOps(entity: StoredQuery) {

    implicit def clausesToDep(clauses: Map[Int, BoolClause]): Dependencies = clauses.flatMap {
        case (k, v: NamedBoolClause) => Some((entity.id, v.storedQueryId) -> k)
        case _ => None
      }

    def create(dependencies: Map[(String, String), Int]): ItemCreated = ItemCreated(entity, entity.clauses)

    def addClause(kv: (Int, BoolClause)) = {
      entity.copy(clauses = entity.clauses + kv)
    }

    def merge(state: StoredQueries) = state.copy(items = state.items + (entity.id -> entity))

    def genClauseId() = {
      def generate(): Int = {
        val id = scala.math.abs(scala.util.Random.nextInt())
        if (entity.clauses.keys.exists(_ == id)) generate() else id
      }
      generate()
    }
  }

  implicit class StoredQueriesOps(state: StoredQueries) {

    def rebuildDependencies(value: (String, Int, BoolClause)) = {
      val (consumer, newClauseId, clause) = value
      clause match {
        case NamedBoolClause(provider, _, _, _) => acyclicProofing((consumer, provider) -> newClauseId)
        case _ => Some(state.dependencies)
      }
    }

    def acyclicProofing(dependency: ((String,String), Int)): Option[Map[(String, String), Int]] = {
      import TopologicalSort._
      val data = state.dependencies + dependency
      Try(sort(toPredecessor(data.keys))) match {
        case Success(_) => Some(data)
        case Failure(_) => None
      }
    }

    def cascadingUpdate(from: String): ItemsChanged = {
      val zero = (state.items, List(from))
      val (updatedItems, changesList) = TopologicalSort.collectPaths(from)(TopologicalSort.toPredecessor(state.dependencies.keys)).flatten.foldLeft(zero) { (acc, link) =>
        val (provider: String, consumer: String) = link
        val (accItems, changes) = acc
        val clauseId = state.dependencies(consumer, provider)
        val updatedNamedBoolClause = accItems(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
          .copy(clauses = accItems(provider).clauses)
        val updatedConsumer = accItems(consumer).copy(clauses = accItems(consumer).clauses + (clauseId -> updatedNamedBoolClause))
        (accItems + (consumer -> updatedConsumer), consumer :: changes)
      }
      ItemsChanged(updatedItems.toSeq, changesList, state.dependencies)
    }

    def update(event: Event): StoredQueries = {
      def escalateVer(id: String) = id -> (state.changes.getOrElse(id, 0) + 1)
      event match {
        case ItemCreated(entity, dp) =>
          state.copy(items = state.items + (entity.id -> entity), dependencies = dp, changes = state.changes + escalateVer(entity.id))
        case ItemsChanged(xs, changesList, dp) =>
          state.copy(items = state.items ++ xs, dependencies = dp, changes = state.changes ++ changesList.map(escalateVer))
      }
    }
  }
}

class StoredQueryAggregateRoot extends PersistentActor with akka.actor.ActorLogging {

  import StoredQueryAggregateRoot._

  val persistenceId: String = Id
  var state = StoredQueries()


  def dependencies: Dependencies = state.dependencies

  implicit def getItem(storedQueryId: String): Option[StoredQuery] = state.items.get(storedQueryId)
  implicit def getItem2(referredId: Option[String]): Option[Option[StoredQuery]] = referredId.map(state.items.get)

  def genItemId = {
    def generateNewItemId: String = {
      val id = scala.math.abs(scala.util.Random.nextInt()).toString
      if (state.items.keys.exists(_ == id)) generateNewItemId else id
    }
    generateNewItemId
  }

  def afterPersisted(`sender`: ActorRef, evt: Event, custom: Option[Any]) = {
    state = state.update(evt)
    custom.foreach { m => log.info(s"$m") }
    `sender` ! custom.getOrElse(evt)
  }

  def doPersist(evt: Event, custom: Option[Any] = None) = persist(evt)(afterPersisted(sender(), _, custom))

  val receiveRecover: Receive = {
    case evt: Event => state = state.update(evt)
    case SnapshotOffer(_, snapshot: StoredQueries) => state = snapshot
  }

  val receiveCommand: Receive = {
    case CreateNewStoredQuery(title, referredId, tags) =>
      val ack = CreatedAck(s"$genItemId")
      implicit def convertToEvent(src: StoredQuery): Event = src.copy(id = ack.id, title = title, tags = tags ++ tags).create(dependencies)
      (referredId: Option[Option[StoredQuery]]) match {
        case Some(None)      => sender() ! s"$referredId is not exist."
        case Some(Some(src)) => doPersist(src, Some(ack))
        case None            => doPersist(StoredQuery(), Some(ack))
      }

    case AddClause(storedQueryId, clause) =>
      (storedQueryId: Option[StoredQuery]) match {
        case None              => sender() ! "not found"
        case Some(storedQuery) =>
          val newClauseId = storedQuery.genClauseId()
          state.rebuildDependencies((storedQueryId, newClauseId, clause)) match {
            case None     => sender() ! "CycleInDirectedGraphError"
            case Some(dp) => doPersist(storedQuery.addClause(newClauseId -> clause).merge(state.copy(dependencies = dp)).cascadingUpdate(storedQueryId), Some(CreatedAck(s"$newClauseId")))
          }
      }
    case "snap" => saveSnapshot(state)
    case unknown: Any =>
      context.system.log.warning(s"unexpected message: $unknown")
  }
}