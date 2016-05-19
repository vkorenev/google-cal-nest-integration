name := "google-cal-nest-integration"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

val specs2Version = "3.8.3-20160524134053-8145e17"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % specs2Version % "test",
  "org.specs2" %% "specs2-mock" % specs2Version % "test",
  "org.specs2" %% "specs2-junit" % specs2Version % "test"
)

scalacOptions in Test += "-Yrangepos"
