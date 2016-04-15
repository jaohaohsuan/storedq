package kb.node.storedq.api

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions
import scala.concurrent.duration._
import kb.node.storedq.domain.{CreateStoredQuery, CreatedAck, AddClause, MatchBoolClause, BoolClause, RemoveClauses, SuccessAck, NamedBoolClause}

/**
  * Created by henry on 4/13/16.
  */

object Service {

  def singleField(field: String) = """\s+""".r.findFirstIn(field).isEmpty
  def queryFieldConstrain(field: String) = field.matches("""^dialogs$|^agent\*$|^customer\*$""")

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r

  final case class StoredQuery(title: String, tags: Option[String]){
    require( title.nonEmpty )
  }

  case class NamedClause(storedQueryId: String, storedQueryTitle: String, occur: String) {
    require(test)
    def test = occur.matches(OccurrenceRegex.toString())
  }

  case class MatchClause(query: String, field: String, operator: String, occur: String) {
    require(test)
    require(singleField(field), s"single field only")
    require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
    def test = operator.matches("^[oO][rR]$|^[Aa][Nn][Dd]$") && occur.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty
  }

  implicit def toSet(value : Option[String]): Set[String] = value.map { _.split("""\s+""").toSet }.getOrElse(Set.empty)

  implicit def toCmd(e: StoredQuery): CreateStoredQuery= {
    CreateStoredQuery(e.title, None, e.tags)
  }

  implicit def toBoolClause(e: MatchClause): BoolClause = {
    MatchBoolClause(e.query, e.field, e.operator, e.occur)
  }

  implicit def toBoolClause(e: NamedClause): BoolClause = {
    NamedBoolClause(e.storedQueryId, e.storedQueryTitle, e.occur)
  }


  implicit val storedQueryFormat = jsonFormat2(StoredQuery)
  implicit val matchClauseFormat = jsonFormat4(MatchClause)
  implicit val namedClauseFormat = jsonFormat3(NamedClause)
}

trait Service {

  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer
  implicit val timeout = Timeout(5.seconds)

  import Service._

  lazy val aggRootProxy = system.actorSelection("/user/storedQueryAggregateRootProxy")

  def create(cmd: CreateStoredQuery): Route = onComplete(aggRootProxy ? cmd) {
    case Success(CreatedAck(id)) => complete(Created, id)
    case Success(unexpected) => complete(InternalServerError, s"$unexpected")
    case Failure(ex) => complete(InternalServerError)
  }

  def addClause(clause: BoolClause)(implicit storedQueryId: String): Route = onComplete(aggRootProxy ? AddClause(storedQueryId, clause)) {
    case Success(CreatedAck(id)) => complete(Created, id)
    case Success(unexpected) => complete(InternalServerError, s"$unexpected")
    case Failure(ex) => complete(InternalServerError)
  }

  def deleteClause(id: Int)(implicit storedQueryId: String): Route =
    onComplete(aggRootProxy ? RemoveClauses(storedQueryId, List(id))) {
      case Success(SuccessAck) => complete(NoContent)
      case Success(unexpected) => complete(InternalServerError, s"$unexpected")
      case Failure(ex) => complete(InternalServerError)
  }

  val routes = {
    pathPrefix("api" / "v1" / "storedq") {
      post {
        pathEnd {
          entity(as[StoredQuery]) { entity =>
            create(entity)
          }
        } ~
        pathPrefix(Segment) { implicit storedQueryId =>
          pathEnd {
            entity(as[StoredQuery]) { entity =>
              create(toCmd(entity).copy(referredId = Some(storedQueryId)))
            }
          } ~
          path("clauses") {
            entity(as[MatchClause]) { entity =>
              addClause(entity)
            } ~
            entity(as[NamedClause]) { entity =>
              addClause(entity)
            }
          }
        }
      } ~
      delete {
        path(Segment / "clauses" / IntNumber) { (storedQueryId, clauseId) =>
          deleteClause(clauseId)(storedQueryId)
        }
      }
    }
  }
}


