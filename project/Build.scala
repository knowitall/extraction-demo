import sbt._
import Keys._

object NlpToolsBuild extends Build {
  // settings
  val buildOrganization = "edu.washington.cs.knowitall.extraction-demo"
  val buildVersion = "1.0.0-SNAPSHOT"
  val buildScalaVersion = "2.10.1"

  // global settings
  val buildSettings = Seq(
    scalaVersion in ThisBuild := buildScalaVersion,
    organization in ThisBuild := buildOrganization
  )

  lazy val root = Project(id = "extraction-demo", base = file("."), settings = Project.defaultSettings ++ buildSettings) aggregate(populator, webapp)

  lazy val populator = Project(id = "populator", base = file("populator"))
  lazy val webapp = Project(id = "webapp", base = file("webapp"))
}
