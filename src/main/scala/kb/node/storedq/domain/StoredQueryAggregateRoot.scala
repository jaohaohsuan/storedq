package kb.node.storedq.domain

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import kb.node.storedq.{domain, _}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Created by henry on 4/8/16.
  */
trait Event
trait State
trait Command
trait Ack

trait BoolClause {
  val occur: String
}
case class MatchBoolClause(query: String, field: String, operator: String, occur: String) extends BoolClause
case class NamedBoolClause(storedQueryId: String, title: String, occur: String, clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

// commands
case object Initial extends Command
case class CreateStoredQuery(title: String, referredId: Option[String], tags: Set[String]) extends Command
case class AddClause(storedQueryId: String, clause: BoolClause) extends Command
case class RemoveClauses(storedQueryId: String, ids: List[Int]) extends Command
case class UpdateStoredQuery(storedQueryId: String, title: String, tags: Option[String]) extends Command

// events
case class ItemCreated(entity: StoredQuery, dependencies: Map[(String, String), Int]) extends Event
case class ItemsChanged(items: Seq[(String, StoredQuery)], changes: Map[String, Long], dependencies: Map[(String, String), Int]) extends Event

// state
case class StoredQueries(items: Map[String, StoredQuery] = Map.empty,
                         dependencies: Map[(String, String), Int] = Map.empty, changes: Map[String, Long] = Map.empty) extends State

case class CreateStoredQueryAck(id: String) extends Ack
case class AddClauseAck(id: String) extends Ack
case object SuccessAck extends  Ack

object StoredQueryAggregateRoot {

  def props(probe: Option[ActorRef] = None): Props = Props(new StoredQueryAggregateRoot(probe))

  type Dependencies = Map[(String, String), Int]

  type Changes = (Map[String, StoredQuery], Map[String, Long])

  implicit class StoredQueryOps(entity: StoredQuery) {

    implicit def clausesToDep(clauses: Map[Int, BoolClause]): Dependencies = clauses.flatMap {
        case (k, v: NamedBoolClause) => Some((entity.id, v.storedQueryId) -> k)
        case _ => None
      }

    def create(dependencies: Map[(String, String), Int]): ItemCreated = ItemCreated(entity, entity.clauses)

    def addClause(kv: (Int, BoolClause)) = {
      entity.copy(clauses = entity.clauses + kv)
    }

    def removeClauses(ids: List[Int]): StoredQuery = entity.copy(clauses = entity.clauses -- ids)

    def merge(state: StoredQueries) = state.copy(items = state.items + (entity.id -> entity))

    def genClauseId() = {
      def generate(): Int = {
        val id = scala.math.abs(scala.util.Random.nextInt())
        if (entity.clauses.keys.exists(_ == id)) generate() else id
      }
      generate()
    }

    def materialize(implicit repo: Map[String, StoredQuery]): StoredQuery =
      entity.clauses.foldLeft(entity) { case (acc, (k, v)) =>
        v match {
          case clause@NamedBoolClause(referredStoredQueryId, _, _ , _) =>
            acc.copy(clauses = acc.clauses + (k -> clause.copy(clauses = repo(referredStoredQueryId).materialize.clauses)))
          case _ => acc
        }
      }
  }

  implicit class StoredQueriesOps(state: StoredQueries) {

    def tryBuildDependencies(value: (String, Int, BoolClause)): Option[Map[(String, String), Int]] = {
      val (consumer, newClauseId, clause) = value
      clause match {
        case NamedBoolClause(provider, _, _, _) => acyclicProofing((consumer, provider) -> newClauseId)
        case _ => Some(state.dependencies)
      }
    }

    private def acyclicProofing(dependency: ((String,String), Int)): Option[Map[(String, String), Int]] = {
      import TopologicalSort._
      val data = state.dependencies + dependency
      Try(sort(toPredecessor(data.keys))) match {
        case Success(_) => Some(data)
        case Failure(_) => None
      }
    }

    def cascadingUpdate(from: String): ItemsChanged = {

      def withVer(id: String) = id -> state.changes(id)

      val paths = TopologicalSort.collectPaths(from)(TopologicalSort.toPredecessor(state.dependencies.keys))
      val (latestItems, changes) = paths.flatten.foldLeft((state.items, Map(withVer(from)))) { (acc, path) =>
        val (repo, changes) = acc
        val (provider: String, consumer: String) = path
        val clauseId = state.dependencies(consumer, provider) // invert path
        val updatedClause = clauseId -> repo(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause].copy(clauses = repo(provider).clauses)
        val updatedItem = consumer -> repo(consumer).copy(clauses = repo(consumer).clauses + updatedClause)

        (repo + updatedItem, changes + withVer(consumer))
      }
      ItemsChanged(changes.map { case (id, _) => id -> latestItems(id).materialize(latestItems) }.toSeq, changes, state.dependencies)
    }

    def update(event: Event): StoredQueries = {
      event match {
        case ItemCreated(entity, dp) =>
          state.copy(items = state.items + (entity.id -> entity), dependencies = dp, changes = state.changes + (entity.id -> 1l))
        case ItemsChanged(xs, changesList, dp) =>
          state.copy(items = state.items ++ xs, dependencies = dp, changes = state.changes ++ changesList.map { case (k,ver) => k -> (ver + 1l) })
      }
    }
  }
}

class StoredQueryAggregateRoot(probe: Option[ActorRef]) extends PersistentActor with akka.actor.ActorLogging {

  import StoredQueryAggregateRoot._

  val persistenceId: String = "storedQueryAggRoot"
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
    probe.foreach{ p => p ! state }
    custom.foreach { m => log.debug(s"$m") }
    `sender` ! custom.getOrElse(evt)
  }

  def doPersist(evt: Event, custom: Option[Any] = None) = persist(evt)(afterPersisted(sender(), _, custom))

  val receiveRecover: Receive = {
    case evt: Event => {
      state = state.update(evt)
      state.changes.foreach { case (k,v) => log.debug(s"id:$k version:$v ")}
    }
    case SnapshotOffer(_, snapshot: StoredQueries) => state = snapshot
  }

  val receiveCommand: Receive = {
    case CreateStoredQuery(title, referredId, tags) =>
      val ack = CreateStoredQueryAck(s"$genItemId")
      implicit def convertToEvent(src: StoredQuery): Event = src.copy(id = ack.id, title = title, tags = tags ++ tags).create(dependencies)
      (referredId: Option[Option[StoredQuery]]) match {
        case Some(None)      => sender() ! s"$referredId is not exist."
        case Some(Some(src)) => doPersist(src, Some(ack))
        case None            => doPersist(domain.StoredQuery(), Some(ack))
      }

    case UpdateStoredQuery(storedQueryId, title, tags) =>


    case AddClause(storedQueryId, clause) =>
      (storedQueryId: Option[StoredQuery]) match {
        case None              => sender() ! "not found"
        case Some(storedQuery) =>
          val newClauseId = storedQuery.genClauseId()
          state.tryBuildDependencies((storedQueryId, newClauseId, clause)) match {
            case None     => sender() ! "CycleInDirectedGraph"
            case Some(dp) => doPersist(storedQuery.addClause(newClauseId -> clause).merge(state.copy(dependencies = dp)).cascadingUpdate(storedQueryId), Some(AddClauseAck(s"$newClauseId")))
          }
      }

    case RemoveClauses(storedQueryId, ids) =>
      (storedQueryId: Option[StoredQuery]) match {
        case None              => sender() ! "not found"
        case Some(storedQuery) =>
          implicit def clausesToDp(clauseIds: List[Int]): Map[String, String] =  storedQuery.clauses.flatMap {
              case (k, v: NamedBoolClause) if ids.contains(k) => Some((storedQueryId, v.storedQueryId))
              case (k, v) => None
            }
          doPersist(storedQuery.removeClauses(ids).merge(state.copy(dependencies = state.dependencies -- ids)).cascadingUpdate(storedQueryId), Some(SuccessAck))
      }

    case "snap" => saveSnapshot(state)
    case unknown: Any =>
      context.system.log.warning(s"unexpected message: $unknown")
  }
}