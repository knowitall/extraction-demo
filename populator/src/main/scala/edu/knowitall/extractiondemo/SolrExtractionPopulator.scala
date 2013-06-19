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
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer
import org.apache.solr.common.SolrInputDocument
import java.util.concurrent.atomic.AtomicInteger

object SolrExtractionPopulator {
  val logger = LoggerFactory.getLogger(this.getClass)

  abstract class Settings {
    def inputDirectory: File
    def recursive: Boolean

    def corpusName: String

    def taggerPath: Option[String]

    def solrUrl: String // "jdbc:mysql://localhost/kdd?rewriteBatchedStatements=true";

    // to be used instead of solrUrl
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

    val batchSize = 1000
    val batchesPerCommit = 10
    
    val solr = new ConcurrentUpdateSolrServer(settings.solrUrl, batchSize, 8)

    val writer = settings.outputFile.map(new java.io.PrintWriter(_))
    val extractor = new ExtractionPopulator(taggers, true) {
      val numDocs = new AtomicInteger(0)
      def persist(documentEntity: DocumentEntity) {}
      def persist(sentenceEntity: SentenceEntity) {
        for (extr <- sentenceEntity.extractions) {
          val doc = new SolrInputDocument()
          doc.addField("id", extr.id)
          doc.addField("arg1", extr.arg1)
          doc.addField("rel", extr.rel)
          doc.addField("arg2", extr.arg2)
          doc.addField("arg1_postag", extr.sentence.tokens(extr.arg1Interval).map(_.postag).mkString(" ") )
          doc.addField("rel_postag", extr.sentence.tokens(extr.relInterval).map(_.postag).mkString(" ") )
          doc.addField("arg2_postag", extr.sentence.tokens(extr.arg2Interval).map(_.postag).mkString(" ") )
          extr.arg1Types(sentenceEntity.types).foreach { typ => doc.addField("arg1_types", typ) }
          extr.relTypes(sentenceEntity.types).foreach { typ => doc.addField("rel_types", typ) }
          extr.arg2Types(sentenceEntity.types).foreach { typ => doc.addField("arg2_types", typ) }
          doc.addField("context", "")
          doc.addField("confidence", extr.confidence)
          doc.addField("sentence", sentenceEntity.text)
          doc.addField("extractor", extr.extractor)
          doc.addField("url", sentenceEntity.document.path)
          doc.addField("corpus", sentenceEntity.document.corpus)
          
          solr.add(doc)
          if (numDocs.incrementAndGet() % batchSize * batchesPerCommit == 0) {
            solr.commit()
            System.err.println("%d docs indexed.".format(numDocs.get()))
          }
        }
      }
    }

    println("Listing files...")
    val files = FileUtils.listFiles(settings.inputDirectory, null, settings.recursive).asScala

    println("Extracting and loading...")
    for (file <- files) {
      try {
        println(file.getName)
        val corpus = try {
          file.getPath.drop(settings.inputDirectory.getPath.size).drop(1).takeWhile(_ != '/')
        }
        catch {
          case e: Exception => "unknown"
        }
        extractor.extractAndPersist(file, corpus)
        println()
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }
}
