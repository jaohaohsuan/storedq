import com.typesafe.sbt.packager.docker._
import sbt.Keys._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, DockerPlugin).settings(
  name := "storedq",
  version := "1.0.10",
  scalaVersion := "2.11.8",
  scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  evictionWarningOptions in update := EvictionWarningOptions.empty,
  libraryDependencies ++= {
    val akkaVersion = "2.4.3"
    Seq(
      "io.circe" %% "circe-core" % "0.3.0",
      "io.circe" %% "circe-generic" % "0.3.0",
      "io.circe" %% "circe-parser" % "0.3.0",
      "ch.qos.logback" % "logback-classic" % "1.1.3",
      "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1",
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.11",
      //"com.propensive" %% "rapture-json-circe" % "2.0.0-M5",
      "org.json4s" %% "json4s-native" % "3.3.0",
      "org.scalatra.scalate" %% "scalate-core" % "1.7.1",
      "org.scalactic" %% "scalactic" % "2.2.6",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    )
  },
  dockerRepository := Some("127.0.0.1:5000/inu"),
  packageName in Docker := "storedq",
  dockerCommands := Seq(
    Cmd("FROM", "java:latest"),
    Cmd("ENV", "REFRESHED_AT", "2016-04-08"),
    Cmd("RUN", "apt-get update && apt-get install -y apt-utils dnsutils && apt-get clean && rm -rf /var/lib/apt/lists/*"),
    Cmd("WORKDIR", "/opt/docker"),
    Cmd("ADD", "opt", "/opt"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
    Cmd("EXPOSE", "2551"),
    Cmd("USER", "daemon"),
    Cmd("ENTRYPOINT", "bin/storedq")
  ),
  bashScriptExtraDefines += """
                              |my_ip=$(hostname --ip-address)
                              |
                              |function format {
                              |  local fqdn=$1
                              |
                              |  local result=$(host $fqdn | \
                              |    grep -v "not found" | grep -v "connection timed out" | \
                              |    grep -v $my_ip | \
                              |    sort | \
                              |    head -5 | \
                              |    awk '{print $4}' | \
                              |    xargs | \
                              |    sed -e 's/ /,/g')
                              |  if [ ! -z "$result" ]; then
                              |    export $2=$result
                              |  fi
                              |}
                              |
                              |format $PEER_DISCOVERY_SERVICE SEED_NODES
                              |format $AKKA_PERSISTENCE_SERVICE CASSANDRA_NODES
                              |""".stripMargin
)