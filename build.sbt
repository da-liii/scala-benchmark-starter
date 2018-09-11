scalaVersion in ThisBuild := "2.11.12"

fork := true

lazy val benchmark = (project in file("benchmark")).settings(
  name := "benchmark",
  libraryDependencies ++= Seq(
    "org.openjdk.jmh" % "jmh-core" % "1.21",
  )
).enablePlugins(JmhPlugin)
