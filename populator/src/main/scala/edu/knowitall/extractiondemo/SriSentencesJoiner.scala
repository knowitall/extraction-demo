package edu.knowitall.extractiondemo

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.io.Source

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import edu.knowitall.common.Resource
import scopt.OptionParser

object SriSentencesJoiner  {
  val logger = LoggerFactory.getLogger(this.getClass)

  abstract class Settings {
    def inputDirectory: File
    def outputDirectory: File
    def recursive: Boolean
    def parser: String
  }

  def main(args: Array[String]) = {
    object settings extends Settings {
      var inputDirectory: File = _
      var outputDirectory: File = _
      var recursive: Boolean = false
      var parser: String = "stanford"
    }

    val parser = new OptionParser("joiner") {
      arg("input", "sentences input directory", { path: String => settings.inputDirectory = new File(path) })
      arg("output", "sentences output directory", { path: String => settings.outputDirectory = new File(path) })

      opt("r", "recursive", "search directories recursively", { settings.recursive = true})
    }

    if (parser.parse(args)) {
      run(settings)
    }
  }

  def run(settings: Settings) = {
    import scala.collection.JavaConversions._
    logger.info("Listing files...")
    val files: Iterable[File] = FileUtils.listFiles(settings.inputDirectory, null, true).asScala
    logger.info("File count: " + files.size)

    val nameRegex = """(.+)_seg_\d+\.\d+_\d+""".r
    def collapseName(name: String) = name match {
      case nameRegex(name) => Some(name)
      case _ => None
    }

    val i = new AtomicInteger
    for (file <- files) {
      collapseName(file.getName) match {
        case Some(name) =>
          logger.info("Processing " + file.getName + " --> " + name + " (" + i.getAndIncrement + "/" + files.size + ")...")
          val output = new File(settings.outputDirectory, name)
          Resource.using(Source.fromFile(file)) { source =>
            Resource.using(new PrintWriter(new OutputStreamWriter(new FileOutputStream(output, true), "UTF8"))) { writer =>
              for (line <- source.getLines) {
                writer.println(line)
              }
            }
          }
        case None =>
	      logger.info("Skipping " + file.getName)
      }
    }
  }
}
