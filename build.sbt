import com.typesafe.sbt.packager.docker._
import sbt.Keys._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, DockerPlugin).settings(
  name := "storedq",

  version := "1.0",

  scalaVersion := "2.11.8",

  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),

  libraryDependencies ++= {
    val akkaVersion = "2.4.2"
    Seq(
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "org.scalatra.scalate" %% "scalate-core" % "1.7.1",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    )
  },
  dockerBaseImage := "java:latest",
  dockerRepository := Some("127.0.0.1:5000/inu"),
  dockerExposedPorts := Seq(2551),
  packageName in Docker := "storedq",
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    ExecCmd("RUN", "apt-get", "-qq", "update"),
    ExecCmd("RUN", "apt-get", "-yq", "install", "apt-utils", "dnsutils"),
    ExecCmd("RUN", "apt-get", "clean"),
    ExecCmd("RUN", "rm", "-rf", "/var/lib/apt/lists/*")
  ),
  bashScriptExtraDefines += """
my_ip=$(hostname --ip-address)

SEED_NODES=$(host $PEER_DISCOVERY_SERVICE | \
    grep -v "not found" | grep -v "connection timed out" | \
    grep -v $my_ip | \
    sort | \
    head -5 | \
    awk '{print $4}' | \
    xargs | \
    sed -e 's/ /,/g')

if [ ! -z "$SEED_NODES" ]; then
    export SEED_NODES
fi"""

)

