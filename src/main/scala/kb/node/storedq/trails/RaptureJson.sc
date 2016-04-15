import org.json4s._
import org.json4s.native.JsonMethods._

val rawJson = """{"hello": "world", "age": 42}"""
val x: JValue = parse(rawJson)
//import rapture.json._
//import rapture.json.jsonBackends.circe._
//
//
//
//val jb = JsonBuffer.empty
//val l1 = json"""{"rel" : "/_search", "name" : "search"}"""
//
//jb.links += l1
//
////println(Json.format(jb))
import kb.node.storedq.domain.{MatchBoolClause, BoolClause}
val matchQuery = MatchBoolClause("this is a test", "agent consumer", "and", "must")
val matchQuery2 = MatchBoolClause("this is a test", "agent consumer", "or", "must")
//
//val int = 100
//val oc = "must"
//
//val prop1 = json"""{ "name": "henry", $oc: false, "int": "42" }"""
////val j1 = Json.parse(prop1)
//val z = JsonBuffer.empty
val j: JValue = parse("""{ "a": 100 }""")
val zero: JValue = parse("""{ "bool": { } }""")
val r = List(matchQuery,matchQuery2).foldLeft(parse("""{ "bool": { } }""")) { case (acc, c) =>
  import org.json4s.JsonDSL._
  val query: JValue = c match {
    case MatchBoolClause(q,f,o,occur) =>
      "bool" ->
        (occur -> Set(
          "multi_match" ->
            ("query" -> q) ~
            ("field" -> f.split("""\s+""").toList) ~
            ("operator" -> o) ~
            ("meta" -> j)
         )
        )
  }
  acc merge query
}

pretty(render(r))