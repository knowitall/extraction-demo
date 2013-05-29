package edu.knowitall.extractiondemo

import java.io.File
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import dispatch._
import dispatch.Defaults._
import edu.knowitall.common.Resource.using
import edu.knowitall.extractiondemo.orm.DocumentEntity
import edu.knowitall.extractiondemo.orm.SentenceEntity
import scopt.OptionParser
import edu.knowitall.taggers.tag.TaggerCollection

object SolrExtractionPopulator {
  val logger = LoggerFactory.getLogger(this.getClass)

  abstract class Settings {
    def inputDirectory: File
    def recursive: Boolean

    def corpusName: String

    def taggerPath: Option[String]

    def solrUrl: String // "jdbc:mysql://localhost/kdd?rewriteBatchedStatements=true";

    def outputFile: Option[File]
  }

  def main(args: Array[String]) {
    object settings extends Settings {
      var inputDirectory: File = _
      var recursive = false

      var corpusName: String = ""

      var outputFile: Option[File] = None

      var taggerPath: Option[String] = None

      var solrUrl: String = _
    }

    val parser = new OptionParser("extrpop") {
      arg("directory", "text input directory", { path: String => settings.inputDirectory = new File(path) })
      arg("connection", "SOLR url", { s: String => settings.solrUrl = s })
      opt("o", "output-file", "optional output file for json", { file => settings.outputFile = Some(new File(file)) })
      opt("r", "recursive", "recursively descent into subdirectories", { settings.recursive = true })
      opt("c", "corpus", "corpus name", { s: String => settings.corpusName = s })

      opt("t", "taggers", "path to taggers", { path => settings.taggerPath = Some(path) })
    }

    if (parser.parse(args)) {
      run(settings)
    }
  }

  def run(settings: Settings) = {
    val taggers = (settings.taggerPath map TaggerCollection.fromPath) getOrElse (new TaggerCollection)

    import dispatch._

    def solrUrl = url(settings.solrUrl).as_!("knowitall", "knowit!")
    val http = Http()

    val writer = settings.outputFile.map(new java.io.PrintWriter(_))
    val extractor = new ExtractionPopulator(taggers, true) {
      def persist(documentEntity: DocumentEntity) {}
      def persist(sentenceEntity: SentenceEntity) {
        val docs = for (extr <- sentenceEntity.extractions) yield {
          <doc>
            <field name="id">{ extr.id }</field>

            <field name="arg1">{ extr.arg1 }</field>
            <field name="rel">{ extr.rel }</field>
            { extr.arg2s.map { arg2 =>
              <field name="arg2">{ arg2 }</field>
            }}

            <field name="arg1_postag">{ extr.sentence.tokens(extr.arg1Interval).map(_.postag).mkString(" ") }</field>
            <field name="rel_postag">{ extr.sentence.tokens(extr.relInterval).map(_.postag).mkString(" ") }</field>
            <field name="arg2_postag">{ extr.sentence.tokens(extr.arg2Interval).map(_.postag).mkString(" ") }</field>

            { extr.arg1Types(sentenceEntity.types).map { typ =>
              <field name="arg1_types">{ typ.descriptor }</field>
            }}
            { extr.relTypes(sentenceEntity.types).map { typ =>
              <field name="rel_types">{ typ.descriptor }</field>
            }}
            { extr.arg2Types(sentenceEntity.types).map { typ =>
              <field name="arg2_types">{ typ.descriptor }</field>
            }}

            <field name="context"></field>

            <field name="confidence">{ extr.confidence }</field>

            <field name="sentence">{ sentenceEntity.text }</field>

            <field name="extractor">{ extr.extractor }</field>
            <field name="url">{ sentenceEntity.document.path }</field>
          </doc>
        }
        val xml = <add>{docs}</add>
        val xmlString = xml.toString

        val headers = Map("Content-type" -> "application/xml")
        val req = solrUrl / "update" << xmlString <:< headers
        writer foreach (_.println(xmlString))
        http(req OK as.String).either() match {
          case Right(content) =>
          case Left(StatusCode(404)) => System.err.println("404 not found: " + settings.solrUrl)
          case Left(code) => System.err.println("error code: " + code.toString)
        }
      }
    }

    println("Listing files...")
    val files = FileUtils.listFiles(settings.inputDirectory, null, settings.recursive).asScala

    println("Extracting and loading...")
    for (file <- files) {
      try {
        println(file.getName)
        extractor.extractAndPersist(file)
        println()
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }

    // commit the updates
    println("Committing the changes...")
    println(http((solrUrl / "update" <<? Map("commit" -> "true")) OK as.String).apply())
    http.shutdown()
  }
}
