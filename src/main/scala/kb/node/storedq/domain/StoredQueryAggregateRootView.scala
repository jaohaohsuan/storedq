package kb.node.storedq.domain

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import org.json4s.JValue

import scala.language.implicitConversions

/**
  * Created by henry on 4/14/16.
  */
class StoredQueryAggregateRootView extends Actor  {

  val readJournal =
    PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val source = readJournal.eventsByPersistenceId("storedQueryAggRoot", 0, Long.MaxValue)

  implicit def getItem(id: String)(implicit repo: Map[String, StoredQuery]): StoredQuery = repo(id)
  implicit def getClauses(clauses: Map[Int, BoolClause]): Iterable[BoolClause] = clauses.values

  //var storedQueries: Map[String, StoredQuery] = Map.empty

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) =>
        entity :: Nil
      case ItemsChanged(items, changes, _) => items.foldLeft(List.empty[StoredQuery]){ case (acc,(_,v)) => v :: acc }
      case _ => Nil
    }
  }

  def toBoolQuery(clauses: Iterable[BoolClause]): JValue = {
    import org.json4s._
    import org.json4s.native.JsonMethods._
    import org.json4s.JsonDSL._

    clauses.foldLeft(parse("""{ "bool": { } }""")) { (acc, clause) =>
      val query: JValue = clause match {
        case MatchBoolClause(q,f,o,occur) =>
          "bool" ->
            (occur -> Set(
              "multi_match" ->
                ("query" -> q) ~
                  ("field" -> f.split("""\s+""").toList) ~
                  ("operator" -> o)
            ))
        case NamedBoolClause(_, _, occur, innerClauses) => "bool" -> (occur -> Set(toBoolQuery(innerClauses)))
        case _ => parse("""{ "bool": { } }""")
      }
      acc merge query
    }
  }

  implicit val mat = ActorMaterializer()

  def receive: Receive = {
    case _ =>

      source.mapConcat(flatten)
            .runForeach(println)
      source.mapConcat(flatten)
            .map { s => toBoolQuery(s.clauses) }
            .runForeach { event =>
              import org.json4s.native.JsonMethods._
              println(pretty(render(event)))
            }
  }
}