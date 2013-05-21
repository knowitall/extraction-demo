package edu.knowitall.extractiondemo.orm

import edu.knowitall.collection.immutable.Interval
import edu.knowitall.tool.chunk.ChunkedToken
import edu.knowitall.tool.stem.Lemmatized

class TokenEntity {
  var id: Long = _
  var sentence: SentenceEntity = _

  var position: Int = _
  var token: Lemmatized[ChunkedToken] = _

  def string: String = token.string
  def lemma: String = token.lemma
  def postag: String = token.postag
  def chunk: String = token.chunk
}

class SentenceEntity {
  var id: Long = _
  var document: DocumentEntity = _
  var extractions: Seq[ExtractionEntity] = _

  var text: String = _
  var tokens: Seq[TokenEntity] = _
  var types: Set[TypeEntity] = _

  def tokens(interval: Interval): Seq[TokenEntity] = tokens.drop(interval.start).take(interval.size)
}

class TypeEntity {
  var id: Long = _
  var descriptor: String = _
  var text: String = _
  var interval: Interval = _
  def start: Int = interval.start
  def end: Int = interval.end
}

class DocumentEntity {
  var id: Long = _
  var name: String = _
  var path: String = _
}

class ExtractionEntity {
  var id: Long = _
  var sentence: SentenceEntity = _

  var confidence: Double = _

  var extractor: String = _

  var rel: String = _
  var arg1: String = _
  var arg2: String = _

  var arg1Interval: Interval = _
  var relInterval: Interval = _
  var arg2Interval: Interval = _

  def intervals = Iterable(arg1Interval, relInterval, arg2Interval)
  def containedTypes(types: Iterable[TypeEntity]) = {
    types.filter(typ => intervals.exists(_ superset typ.interval))
  }

  def arg1Types(types: Iterable[TypeEntity]) = {
    types.filter(arg1Interval superset _.interval)
  }

  def relTypes(types: Iterable[TypeEntity]) = {
    types.filter(relInterval superset _.interval)
  }

  def arg2Types(types: Iterable[TypeEntity]) = {
    types.filter(arg2Interval superset _.interval)
  }
}
