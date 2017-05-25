name := "shop-akka"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= {
  val akkaVersion = "2.5.1"
  Seq("com.typesafe.akka" % "akka-actor_2.12" % akkaVersion,
    "com.typesafe.akka" % "akka-stream_2.12" % akkaVersion,
    "com.typesafe.akka" % "akka-remote_2.12" % akkaVersion)
}