organization := "edu.washington.cs.knowitall.kdd.populator"

name := "extraction-populator"

version := "1.0.0-SNAPSHOT"

crossScalaVersions := Seq("2.10.0", "2.9.2")

scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head }

libraryDependencies ++= Seq(
      "edu.washington.cs.knowitall.ollie" %% "ollie-core" % "1.0.3",
      "edu.washington.cs.knowitall.chunkedextractor" %% "chunkedextractor" % "1.0.3",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-parse-malt" % "2.4.1",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-chunk-opennlp" % "2.4.1",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-sentence-opennlp" % "2.4.1",
      "com.github.scopt" %% "scopt" % "2.1.0",
      "net.databinder.dispatch" %% "dispatch-core" % "0.10.0",
      "commons-io" % "commons-io" % "2.4"
    )
