package edu.knowitall.extractiondemo

import java.io.File
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConverters._
import scala.io.Source

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import edu.knowitall.common.Resource.using
import edu.knowitall.tool.parse.DependencyParser
import edu.knowitall.tool.parse.MaltParser
import edu.knowitall.tool.parse.ClearParser
import scopt.OptionParser

object SriSentencesProcessor {
  val logger = LoggerFactory.getLogger(this.getClass)

  abstract class Settings {
    def inputDirectory: File
    def outputDirectory: File
    def recursive: Boolean
    def parser: String
    def parallel: Boolean
  }

  def main(args: Array[String]) = {
    object settings extends Settings {
      var inputDirectory: File = _
      var outputDirectory: File = _
      var recursive: Boolean = false
      var parser: String = _
      var parallel: Boolean = false
    }

    val parser = new OptionParser("applypat") {
      arg("input", "sentences input directory", { path: String => settings.inputDirectory = new File(path) })
      arg("output", "sentences output directory", { path: String => settings.outputDirectory = new File(path) })
      arg("parser", "stanford/malt/clear", { parser: String => settings.parser = parser })
      opt("r", "recursive", "recursively descent into subdirectories", { settings.recursive = true })
      opt("parallel", "stanford or malt", { settings.parallel = true })
    }

    if (parser.parse(args)) {
      run(settings)
    }
  }

  def run(settings: Settings) = {
    import scala.collection.JavaConversions._
    logger.info("Listing files...")
    val files: Iterable[File] = FileUtils.listFiles(settings.inputDirectory, Array("sentences", "txt"), settings.recursive).asScala
    logger.info("File count: " + files.size)

    val tagRegex = "<[^>]*>".r
    val tabRegex = "\t".r
    val matchingRegex = """(?:\(.*?\))|(?:\[.*?\])""".r
    logger.info("Initializing parser...")

    val parser =
      settings.parser match {
        //case "stanford" => new StanfordParser
        case "malt" => new MaltParser
        case "clear" => new ClearParser
        case s => throw new IllegalArgumentException("Unknown parser: " + s)
      }

    val i = new AtomicInteger
    val iter = if (settings.parallel) files.par else files
    for (file <- iter) {
      def clean(line: String) = {
        if ((line count (_==':')) >= 4) line.substring(1 + line lastIndexOf ":")
        else if (line contains ":") line.substring(1 + (line indexOf ":"))
        else if (line contains "On ") line.substring(line indexOf ("On "))
        else line
      }

      def valid(line: String) = {
        if (line contains "|") {
          false
        }
        else {
          true
        }
      }

      val subdirectory = file.getParentFile.getPath.drop(settings.inputDirectory.getPath.size)
      val outputDir = new File(settings.outputDirectory, subdirectory)
      outputDir.mkdirs()

      val output = new File(outputDir, file.getName)
      if (output.exists) {
        logger.info("Skipping " + file.getName + " (already processed)")
      }
      else {
        logger.info("Processing " + file.getName + " (" + i.getAndIncrement + "/" + files.size + ")...")
        using(Source.fromFile(file, "UTF8")) { source =>
          using(new PrintWriter(output, "UTF8")) { writer =>
            for (line <- source.getLines; if valid(line)) {
              try {
                val cleaned = clean(line)
                val stripped = matchingRegex.replaceAllIn(tabRegex.replaceAllIn(tagRegex.replaceAllIn(cleaned, ""), " "), "")
                val parsed =
                  if (stripped.length < 500) Some(parser.dependencyGraph(stripped))
                  else None

                writer.println(stripped + "\t" + parsed.getOrElse(""))
              } catch {
                case e: Throwable => logger.error("Error processing line: " + line, e)
              }
            }
          }
        }
      }
    }
  }
}
