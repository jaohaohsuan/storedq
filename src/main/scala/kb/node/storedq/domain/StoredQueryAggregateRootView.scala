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

object StoredQueryAggregateRootView {

  implicit def getItem(id: String)(implicit repo: Map[String, StoredQuery]): StoredQuery = repo(id)

  object BoolQuery {

    import org.json4s.native.JsonMethods._

    val empty: JValue = parse("""{ "bool": {} }""")

    implicit def getClauses(clauses: Map[Int, BoolClause]): Iterable[BoolClause] = clauses.values

    def toBoolQuery(clauses: Iterable[BoolClause]): JValue = {

      import org.json4s._
      import org.json4s.JsonDSL._

      clauses.foldLeft(empty) { (acc, clause) =>
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
          case _ => empty
        }
        acc merge query
      }
    }
  }
}

class StoredQueryAggregateRootView extends Actor  {

  import StoredQueryAggregateRootView._

  val readJournal =
    PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val source = readJournal.eventsByPersistenceId("storedQueryAggRoot", 0, Long.MaxValue)

  private def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) =>
        (entity,0l) :: Nil
      case ItemsChanged(items, changes, _) => items.foldLeft(List.empty[(StoredQuery, Long)]){ case (acc,(k,v)) => (v,changes(k)) :: acc }
      case _ => Nil
    }
  }

  implicit val mat = ActorMaterializer()

  def receive: Receive = {
    case _ =>

      //source.runForeach(println)
      source.mapConcat(flatten)
            .runForeach { case (event,ver) =>
              import org.json4s.native.JsonMethods._
              import org.json4s.JsonDSL._
              import BoolQuery._

              val percolator =
                ("query" -> toBoolQuery(event.clauses)) ~
                ("title" -> event.title) ~
                ("tags" -> event.tags) ~
                ("version" -> ver)

              println(s"${event.id}\n${pretty(render(percolator))}")
            }
  }
}
