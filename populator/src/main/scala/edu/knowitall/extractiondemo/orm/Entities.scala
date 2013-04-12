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
}

class TypeEntity {
  var id: Long = _
  var sentence: SentenceEntity = _
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
}
