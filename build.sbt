import java.text.SimpleDateFormat

import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import java.util.Calendar

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, DockerPlugin).settings(
  name := "storedq",

  version := "1.0.9",

  scalaVersion := "2.11.6",

  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),

  evictionWarningOptions in update := EvictionWarningOptions.empty,

  libraryDependencies ++= {
    val akkaVersion = "2.4.2"
    Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.3",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.11",
      "org.scalatra.scalate" %% "scalate-core" % "1.7.1",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    )
  },
  dockerRepository := Some("127.0.0.1:5000/inu"),
  packageName in Docker := "storedq",
  dockerCommands := Seq(
    Cmd("FROM", "java:latest"),
    Cmd("ENV", "REFRESHED_AT", new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime())),
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
                              |""".stripMargin
)