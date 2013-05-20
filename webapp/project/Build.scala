import sbt._
import Keys._

object ApplicationBuild extends Build {

    val appName         = "webapp"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "edu.washington.cs.knowitall.common-scala" %% "common-scala" % "1.1.1",

      "jp.sf.amateras.solr.scala" %% "solr-scala-client" % "0.0.7"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // Add your own project settings here
      resolvers += "amateras-repo" at "http://amateras.sourceforge.jp/mvn/"
    )

}
