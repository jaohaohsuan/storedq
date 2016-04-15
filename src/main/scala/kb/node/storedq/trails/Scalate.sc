import org.fusesource.scalate._

val engine = new TemplateEngine

val source = "hello {{name}}"

val template = engine.compileMoustache(source)

engine.layout(template.source, Map("name" -> "henry"))

val cluster_seed_nodes =
  s"""seed-nodes = [
     |{{#nodes}}
     |"akka.tcp://{{clusterName}}{{name}}:{{port}}"{{^last}}, {{/last}}
     |{{/nodes}}
     |]""".stripMargin


case class SeedNode(addr: String, last: Boolean = false)

val attributes = List(SeedNode("node1"), SeedNode("node2"))

val l2 = attributes match {
  case Nil => Nil
  case xs :+ y => xs :+ y.copy(last = true)
}


val complex = Map(
  "cluster-name" -> "storedq",
  "port" -> "2551",
  "nodes" -> l2.map { e => Map("name" -> e.addr, "last" -> e.last) }
)

engine.layout(engine.compileMoustache(cluster_seed_nodes).source, complex)

val clusterName = "kube"
val seedPort = 2551

val source2 =
  s"""{{#nodes}}
     |akka.cluster.seed-nodes += "akka.tcp://$clusterName@{{.}}:$seedPort"
     |{{/nodes}}""".stripMargin

val literalSeeds = engine.layout(engine.compileMoustache(source2).source, Map("nodes" -> Array("node1", "node2")))
println(literalSeeds)