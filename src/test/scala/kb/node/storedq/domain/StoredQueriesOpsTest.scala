package kb.node.storedq.domain

import kb.node.storedq.domain.StoredQueryAggregateRoot._
import org.scalatest.PartialFunctionValues._
import org.scalatest.{FlatSpec, Matchers, PartialFunctionValues}

/**
  * Created by henry on 4/17/16.
  */
class StoredQueriesOpsTest extends FlatSpec with Matchers {

  "detecting cycles in a directed graph" should "be found" in {
    val item0 = StoredQuery("1", "query0")
    val item1 = StoredQuery("2", "query1")

    val state0 = List(item0, item1).map(ItemCreated(_, Map.empty)).foldLeft(StoredQueries())( _ update _)

    state0.items should have size 2

    state0.tryBuildDependencies("1", 1, NamedBoolClause("2", "query1", occur = "must"))
          .get.valueAt("1","2") should equal (1)

    state0.tryBuildDependencies("1", 1, NamedBoolClause("2", "query1", occur = "must"))
        .flatMap{ dp =>
        state0.copy(dependencies = dp).tryBuildDependencies("2", 1, NamedBoolClause("1", "query0", occur = "must"))
      } should be (None)
  }

  def addClause(state: StoredQueries)(consumer: StoredQuery, consumerId: String, clauseId: Int, clause: BoolClause) = {
    val paths = state.tryBuildDependencies(consumerId, clauseId, clause).get

    clause match {
      case NamedBoolClause(providerId, _, _ ,_) =>
        paths should contain ((consumerId, providerId)-> clauseId)
      case _ =>
    }

    val newState = consumer.addClause(clauseId, clause).merge(state.copy(dependencies = paths))
    val newEvent = newState.cascadingUpdate(consumerId)

    assert(newEvent.items.size <= newState.items.size)
    newEvent.changes.keys should contain theSameElementsAs newEvent.items.map(_._1)

    (newEvent, newState)
  }

  "modify referenced storedQuery" should "have affect the consumers" in {
    val item0 = StoredQuery("1", "query0")
    val item1 = StoredQuery("2", "query1")

    val state0 = List(item0, item1).map(ItemCreated(_, Map.empty)).foldLeft(StoredQueries())( _ update _)

    val (event0, state1) = addClause(state0)(item0, "1", 1, NamedBoolClause("2", "query1", occur = "must"))
    event0.items should contain ("1" -> item0.addClause(1, NamedBoolClause("2", "query1", occur = "must")))
    event0.changes should contain ("1" -> 1)

    val (event1, _) = addClause(state1.update(event0))(item1,"2", 1, MatchBoolClause("keyword", "message", "and", "must"))
    event1.items should have size 2
    event1.changes should contain ("2" -> 1)
    event1.changes should contain ("1" -> 2)
    val items = event1.items.toMap
    items("1").clauses(1) match {
      case NamedBoolClause("2", "query1", "must", clauses) =>
        clauses.valueAt(1) should equal(MatchBoolClause("keyword", "message", "and", "must"))
      case _ =>
        alert("shouldn't have other clauses")
    }
  }
}
