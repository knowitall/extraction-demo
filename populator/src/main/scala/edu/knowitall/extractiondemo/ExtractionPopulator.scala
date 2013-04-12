package edu.knowitall.extractiondemo
import java.io.File
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import edu.knowitall.extractiondemo.orm._
import scala.actors.threadpool.AtomicInteger
import scopt.OptionParser
import ExtractionPopulator._
import org.slf4j.LoggerFactory
import edu.knowitall.tool.parse.graph.DependencyGraph
import edu.knowitall.extractiondemo.orm._
import edu.knowitall.collection.immutable.Interval
import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.tool.sentence.OpenNlpSentencer
import edu.knowitall.tool.chunk.ChunkedToken
import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.tool.stem.Lemmatized
import edu.knowitall.common.Resource
import scala.io.Source
import edu.knowitall.chunkedextractor.ReVerb
import edu.knowitall.chunkedextractor.Extractor
import edu.knowitall.chunkedextractor.BinaryExtractionInstance
import edu.knowitall.chunkedextractor.Relnoun
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction
import edu.knowitall.chunkedextractor.Nesty
import edu.knowitall.chunkedextractor.R2A2
import edu.knowitall.ollie.Ollie
import edu.knowitall.ollie.confidence.OllieConfidenceFunction
import edu.knowitall.ollie.confidence.OllieConfidenceFunction

object ExtractionPopulator {
  val logger = LoggerFactory.getLogger(this.getClass)
  val tagRegex = "<[^>]*>".r

  abstract class Settings {
    def inputDirectory: File
    def recursive: Boolean

    def taggerPath: Option[String]

    def connectionString: String // "jdbc:mysql://localhost/kdd?rewriteBatchedStatements=true";
    def databaseUsername: String
    def databasePassword: Option[String]

    def showSql: Boolean
  }

  def toJavaTreeSet[T](it: Iterable[T]) = {
    val set = new java.util.TreeSet[T]
    it foreach set.add
    set
  }

  /*
  def main(args: Array[String]) {
    object settings extends Settings {
      var inputDirectory: File = _
      var recursive = false

      var taggerPath: Option[String] = None

      var databaseUsername = "knowitall"
      var databasePassword: Option[String] = None

      var connectionString: String = _

      var showSql: Boolean = false
    }

    val parser = new OptionParser("extrpop") {
      arg("directory", "text input directory", { path: String => settings.inputDirectory = new File(path) })
      arg("connection", "connection string", { s: String => settings.connectionString = s })
      opt("r", "recursive", "recursively descent into subdirectories", { settings.recursive = true })

      opt("t", "taggers", "path to taggers", { path => settings.taggerPath = Some(path) })

      opt("u", "username", "username to connect to database", { s: String => settings.databaseUsername = s })
      opt("p", "password", "password to connect to database", { s: String => settings.databasePassword = Some(s) })

      opt("s", "sql", "show SQL statements", { settings.showSql = true })
    }

    if (parser.parse(args)) {
      run(settings)
    }
  }
  */

  class OpenParseEntityExtractor(ollie: Ollie, confFunc: OllieConfidenceFunction.OllieIndependentConfFunction) extends EntityExtractor("Ollie") {
    def this() = this(new Ollie(), OllieConfidenceFunction.loadDefaultClassifier())
    override def extract(line: Sentence, id: AtomicInteger): List[ExtractionEntity] = {
      for {
        graph <- line.graph.toList;
        inst <- ollie.extract(graph);

        extr = inst.extr
        conf = confFunc.getConf(inst)
      } yield {
        val entity = new ExtractionEntity()
        entity.id = id.getAndIncrement

        entity.rel = extr.rel.text
        entity.arg1 = extr.arg1.text
        entity.arg2 = extr.arg2.text

        entity.relInterval = extr.rel.span
        entity.arg1Interval = extr.arg1.span
        entity.arg2Interval = extr.arg2.span

        entity.extractor = this.name
        entity.confidence = conf

        entity
      }
    }
  }

  abstract class ChunkedEntityExtractor(name: String) extends EntityExtractor(name) {
    def createEntity(id: AtomicInteger, confidence: Double, inst: BinaryExtractionInstance[_]) = {

        val entity = new ExtractionEntity()

        entity.id = id.getAndIncrement

        entity.arg1 = inst.extr.arg1.text
        entity.rel = inst.extr.rel.text
        entity.arg2 = inst.extr.arg2.text

        entity.arg1Interval = inst.extr.arg1.tokenInterval
        entity.relInterval = inst.extr.rel.tokenInterval
        entity.arg2Interval = inst.extr.arg2.tokenInterval

        entity.extractor = name
        entity.confidence = confidence

        entity
    }
  }
  object ChunkedEntityExtractor {
    class ReVerbEntityExtractor extends ChunkedEntityExtractor("ReVerb") {
      val reverb = new ReVerb()
      override def extract(sentence: Sentence, id: AtomicInteger): List[ExtractionEntity] = {
        for {
          sentence <- sentence.chunked.toList
          (conf, inst) <- reverb.extractWithConfidence(sentence.map(_.token))
        } yield {
          createEntity(id, conf, inst)
        }
      }
    }

    class R2A2EntityExtractor extends ChunkedEntityExtractor("R2A2") {
      val r2a2 = new R2A2()
      override def extract(sentence: Sentence, id: AtomicInteger) = {
        for {
          sentence <- sentence.chunked.toList
          (conf, inst) <- r2a2.extractWithConfidence(sentence.map(_.token))
        } yield {
          createEntity(id, conf, inst)
        }
      }
    }

    class RelnounEntityExtractor extends ChunkedEntityExtractor("Relnoun") {
      val relnoun = new Relnoun()
      override def extract(sentence: Sentence, id: AtomicInteger) = {
        for {
          sentence <- sentence.chunked.toList
          inst <- relnoun.extract(sentence)
        } yield {
          createEntity(id, 0.0, inst)
        }
      }
    }

    class NestyEntityExtractor extends ChunkedEntityExtractor("Nesty") {
      val nesty = new Nesty()
      override def extract(sentence: Sentence, id: AtomicInteger) = {
        for {
          sentence <- sentence.chunked.toList
          inst <- nesty.extract(sentence)
        } yield {
          createEntity(id, 0.0, inst)
        }
      }
    }
 }

  case class Sentence (
    val chunked: Option[Seq[Lemmatized[ChunkedToken]]],
    val graph: Option[DependencyGraph]
  )

  case class Document (
    val id: Int,
    val title: String,
    val sentences: Iterator[Sentence]
  )

  abstract class EntityExtractor(val name: String) {
    def extract(line: Sentence, id: AtomicInteger): List[ExtractionEntity]
  }
}

abstract class ExtractionPopulator(
  val extractors: List[EntityExtractor],
  val skipFirstSentence: Boolean) {

  import ExtractionPopulator._

  def this(skipFirstSentence: Boolean) = this(List(
    new ChunkedEntityExtractor.ReVerbEntityExtractor,
    new ChunkedEntityExtractor.R2A2EntityExtractor,
    new ChunkedEntityExtractor.RelnounEntityExtractor,
    new ChunkedEntityExtractor.NestyEntityExtractor,
    new OpenParseEntityExtractor), skipFirstSentence)

  // val sentenceExtractor = new OpenNlpSentencer
  val chunker = new OpenNlpChunker

  val documentId = new AtomicInteger(0)
  val sentenceId = new AtomicInteger(0)
  val relationId = new AtomicInteger(0)
  val tokenId = new AtomicInteger(0)
  val typeId = new AtomicInteger(0)

  /*
  def typesFromSentence(sentence: NlpSentence) = {
    // taggers.tag(sentence)

    for (typ <- sentence.types) yield {
      val entity = new TypeEntity()

      entity.setId(typeId.getAndIncrement)

      entity.setDescriptor(typ.descriptor)
      entity.setText(typ.text)
      entity.setStart(typ.range.getStart)
      entity.setEnd(typ.range.getEnd)

      entity
    }
  }
  */

  def extractSentence(line: Sentence) = {
    for {
      extractor <- extractors;
      entity <- extractor.extract(line, relationId)
    } yield {
      // add types
      /*
      val types = for {
        typ <- typeEntities
        interval = typ.interval
        if (entity.getRelRange.contains(interval) ||
          entity.getArg1Range.contains(interval) ||
          entity.getArg2Range.contains(interval))
      } yield (typ)
      entity.setTypes(toJavaTreeSet(types))
      */

      entity
    }
  }

  def tokensFromSentence(sentence: Seq[Lemmatized[ChunkedToken]]) = {
    for ((token, i) <- sentence.zipWithIndex) yield {
      val entity = new TokenEntity
      entity.id = tokenId.getAndIncrement
      entity.position = i
      entity.token = token

      entity
    }
  }

  def extractLine(string: String) = {
    val parts = string.split("\t")
    val graph =
      if (parts.length > 1)
        try {
          Some(DependencyGraph.deserialize(parts(1)))
        } catch {
          case e: Throwable =>
            logger.error("Error deserializing graph: " + parts(1), e)
            None
        }
      else None
    val text = tagRegex.replaceAllIn(graph.map(_.text) getOrElse string, "")

    val chunked = chunker(text)
    val lemmatized = chunked map MorphaStemmer.lemmatizeToken
    val line = Sentence(Some(lemmatized), graph)

    val entity = new SentenceEntity
    entity.id = sentenceId.getAndIncrement
    entity.text = text

    /*
    val typeEntities = typesFromSentence(sentence)
    typeEntities foreach (_.setSentence(entity))
    */

    def shortEnough(extr: ExtractionEntity) = extr.arg1Interval.size < 255 && extr.arg2Interval.size < 255 && extr.relInterval.size < 255
    val extrEntities = extractSentence(line) filter shortEnough
    extrEntities foreach (_.sentence = entity)

    val tokEntities = tokensFromSentence(lemmatized)
    tokEntities foreach (_.sentence = entity)

    // entity.types = typeEntities
    entity.extractions = extrEntities
    entity.tokens = tokEntities

    entity
  }

  def extractFile(file: File) = {
    Resource.using(Source.fromFile(file, "UTF8")) { source =>
      (for (string <- source.getLines) yield (extractLine(string))).toList
    }
  }

  def persist(entity: SentenceEntity)
  def persist(entity: DocumentEntity)

  def extractAndPersist(file: File) {
    // create document
    val entity = new DocumentEntity()
    entity.id = documentId.getAndIncrement
    entity.name = file.getName
    entity.path = file.getAbsolutePath
    persist(entity)

    for (sentenceEntity <- extractFile(file)) {
      print(".")

      sentenceEntity.document = entity
      persist(sentenceEntity)
    }
  }
}
