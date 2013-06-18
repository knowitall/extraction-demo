package edu.knowitall.extractiondemo
import java.io.File
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import edu.knowitall.extractiondemo.orm._
import java.util.concurrent.atomic.AtomicInteger
import scopt.OptionParser
import ExtractionPopulator._
import org.slf4j.LoggerFactory
import edu.knowitall.tool.parse.graph.DependencyGraph
import edu.knowitall.extractiondemo.orm._
import edu.knowitall.collection.immutable.Interval
import edu.knowitall.tool.tokenize.OpenNlpTokenizer
import edu.knowitall.tool.postag.OpenNlpPostagger
import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.tool.sentence.OpenNlpSentencer
import edu.knowitall.tool.chunk.ChunkedToken
import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.tool.stem.Lemmatized
import edu.knowitall.common.Resource
import edu.knowitall.common.Timing
import scala.io.Source
import edu.knowitall.chunkedextractor.ReVerb
import edu.knowitall.chunkedextractor.Extractor
import edu.knowitall.chunkedextractor.BinaryExtractionInstance
import edu.knowitall.chunkedextractor.Relnoun
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction
import edu.knowitall.chunkedextractor.Nesty
import edu.knowitall.chunkedextractor.R2A2
import edu.knowitall.ollie.Ollie
import edu.knowitall.srlie.SrlExtractor
import edu.knowitall.srlie.SrlExtractionInstance
import edu.knowitall.srlie.confidence.SrlConfidenceFunction
import edu.knowitall.ollie.confidence.OllieConfidenceFunction
import edu.knowitall.ollie.confidence.OllieConfidenceFunction
import edu.knowitall.taggers.tag.TaggerCollection
import edu.knowitall.tool.conf.impl.LogisticRegression

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

  class SrlEntityExtractor(extractor: SrlExtractor, confFunc: LogisticRegression[SrlExtractionInstance])
  extends EntityExtractor("Srl") {
    def this() = this(new SrlExtractor(), SrlConfidenceFunction.loadDefaultClassifier())
    override def extract(line: Sentence, id: AtomicInteger): List[ExtractionEntity] = {
      for {
        graph <- line.graph.toList;
        inst <- extractor.apply(graph);

        extr = inst.extr
        conf = confFunc.getConf(inst)

        if !extr.arg2s.isEmpty
      } yield {
        println(extr)
        val entity = new ExtractionEntity()
        entity.id = id.getAndIncrement

        entity.rel = extr.rel.text
        entity.arg1 = extr.arg1.text
        entity.arg2s = extr.arg2s.map(_.text)

        entity.relInterval = extr.rel.span
        entity.arg1Interval = extr.arg1.interval
        entity.arg2Intervals = extr.arg2s.map(_.interval)

        entity.extractor = this.name
        entity.confidence = conf

        entity
      }
    }
  }

  class OpenParseEntityExtractor(ollie: Ollie, confFunc: OllieConfidenceFunction.OllieIndependentConfFunction)
  extends EntityExtractor("Ollie") {
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

/*
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
*/

    class RelnounEntityExtractor extends ChunkedEntityExtractor("Relnoun") {
      val relnoun = new Relnoun()
      override def extract(sentence: Sentence, id: AtomicInteger) = {
        for {
          sentence <- sentence.chunked.toList
          inst <- relnoun.extract(sentence)
        } yield {
          createEntity(id, 1.0, inst)
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
          createEntity(id, 0.5, inst)
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
  val taggers: TaggerCollection,
  val skipFirstSentence: Boolean) {

  import ExtractionPopulator._

  def this(taggers: TaggerCollection, skipFirstSentence: Boolean) = this(List(
    new ChunkedEntityExtractor.ReVerbEntityExtractor,
    //new ChunkedEntityExtractor.R2A2EntityExtractor,
    new ChunkedEntityExtractor.RelnounEntityExtractor,
    new ChunkedEntityExtractor.NestyEntityExtractor,
    new SrlEntityExtractor), taggers, skipFirstSentence)

  val chunkerModel = OpenNlpChunker.loadDefaultModel
  val postagModel = OpenNlpPostagger.loadDefaultModel
  val tokenModel = OpenNlpTokenizer.loadDefaultModel
  val chunkerLocal = new ThreadLocal[OpenNlpChunker] {
    override def initialValue = {
      val tokenizer = new OpenNlpTokenizer(tokenModel)
      val postagger = new OpenNlpPostagger(postagModel, tokenizer)
      new OpenNlpChunker(chunkerModel, postagger)
    }
  }

  val documentId = new AtomicInteger(0)
  val sentenceId = new AtomicInteger(0)
  val relationId = new AtomicInteger(0)
  val tokenId = new AtomicInteger(0)
  val typeId = new AtomicInteger(0)

  def typesFromSentence(sentence: Sentence): Set[TypeEntity] = {
    val chunked = sentence.chunked.toSet
    chunked.flatMap { chunked =>
      val types = taggers.tag(chunked)

      for (typ <- types) yield {
        val entity = new TypeEntity()

        entity.id = typeId.getAndIncrement

        entity.descriptor = typ.descriptor
        entity.text = typ.text
        entity.interval = typ.interval

        entity
      }
    }
  }

  def extractSentence(line: Sentence) = {
    for {
      extractor <- extractors;
      entity <-
        extractor.extract(line, relationId)
    } yield {
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

    val chunker = chunkerLocal.get()
    val chunked = chunker(text)
    val lemmatized = chunked map MorphaStemmer.lemmatizeToken
    val line = Sentence(Some(lemmatized), graph)

    val entity = new SentenceEntity
    entity.id = sentenceId.getAndIncrement
    entity.text = text

    val typeEntities = typesFromSentence(line)
    entity.types = typeEntities

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
    print("  * Reading lines into memory... ")
    val lines = Timing.timeThen {
      Resource.using(Source.fromFile(file, "UTF8")) { source =>
        source.getLines.toList
      }
    } { ns => println(Timing.Seconds.format(ns)) }

    print("  * Finding extractions in the lines... ")
    Timing.timeThen {
      lines.map(extractLine)
    } { ns => println(Timing.Seconds.format(ns)) }
  }
  
  def extractSource(source: Source) = {
    print("  * Finding extractions in the file... ")
    Timing.timeThen {
      val lines = source.getLines
      lines.map(extractLine)
    } { ns => println(Timing.Seconds.format(ns)) }
  }
  
  def persist(entity: SentenceEntity)
  def persist(entity: DocumentEntity)

  def extractAndPersist(file: File, corpus: String) {
    // create document
    val entity = new DocumentEntity()
    entity.id = documentId.getAndIncrement
    entity.name = file.getName
    entity.path = file.getAbsolutePath
    entity.corpus = corpus
    persist(entity)

    Resource.using(Source.fromFile(file, "UTF8")) { source =>

      val extractions = extractSource(source)

      print("  * Persisting extractions")
      var i = 0
      Timing.timeThen {
        for (sentenceEntity <- extractions) {
          i = i + 1
          if (i % 100 == 0) {
            print(".")
          }

          sentenceEntity.document = entity
          persist(sentenceEntity)
        }
      } { ns =>
        println(" " + Timing.Seconds.format(ns))
      }
      println(i + " sentences persisted")
    }
  }
}
