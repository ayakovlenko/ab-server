name := "ab-server"

scalaVersion := "2.12.4"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "io.monix" %% "monix-eval" % "2.3.0",
  "com.beachape" %% "enumeratum" % "1.5.12",
  "org.jsoup" % "jsoup" % "1.10.3",
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
)

// test dependiencies
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.typesafe.akka" % "akka-testkit_2.12" % "2.5.6" % "test"
)

// play dependencies
libraryDependencies ++= Seq(
  guice,
  ehcache,
)

herokuAppName in Compile := "andys-pizza-bot"
