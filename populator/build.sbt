organization := "edu.washington.cs.knowitall.kdd.populator"

name := "extraction-populator"

version := "1.0.0-SNAPSHOT"

crossScalaVersions := Seq("2.10.0", "2.9.2")

scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head }

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Internal Maven" at "http://knowitall.cs.washington.edu/maven2"

libraryDependencies ++= Seq(
      "edu.washington.cs.knowitall.ollie" %% "ollie-core" % "1.0.4-SNAPSHOT" excludeAll(ExclusionRule(organization = "com.googlecode.clearnlp")),
      "edu.washington.cs.knowitall.chunkedextractor" %% "chunkedextractor" % "1.0.4-SNAPSHOT",
      "edu.washington.cs.knowitall.openiesrl" %% "openie-srl" % "1.0.0-SNAPSHOT" excludeAll(ExclusionRule(organization = "com.googlecode.clearnlp")),
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-parse-malt" % "2.4.2-SNAPSHOT",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-parse-clear" % "2.4.2-SNAPSHOT" excludeAll(ExclusionRule(organization = "com.googlecode.clearnlp")),
      "com.googlecode.clearnlp" % "clearnlp-threadsafe2" % "1.3.0",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-chunk-opennlp" % "2.4.2-SNAPSHOT",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-sentence-opennlp" % "2.4.2-SNAPSHOT",
      "edu.washington.cs.knowitall.nlptools" %% "nlptools-typer-stanford" % "2.4.2-SNAPSHOT",
      "edu.washington.cs.knowitall.taggers" %% "taggers" % "1.0.0-SNAPSHOT",
      "edu.washington.cs.knowitall" % "reverb-core" % "1.4.3-SNAPSHOT",
      "org.slf4j" % "slf4j-api" % "1.7.2",
      "ch.qos.logback" % "logback-classic" % "1.0.9",
      "ch.qos.logback" % "logback-core" % "1.0.9",
      "com.github.scopt" %% "scopt" % "2.1.0",
      "net.databinder.dispatch" %% "dispatch-core" % "0.10.0",
      "commons-io" % "commons-io" % "2.4"
    )
