name := "linkchecker"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.11",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "com.ning" % "async-http-client" % "1.7.19",
  "org.jsoup" % "jsoup" % "1.8.1",
  "ch.qos.logback" % "logback-classic" % "1.0.7")





