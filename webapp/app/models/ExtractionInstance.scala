package models

case class ExtractionInstance(arg1: String, rel: String, arg2: String, sentence: String, extractor: String, count: Int) {
  def arg1s = Seq(arg1)
  def rels = Seq(rel)
  def arg2s = Seq(arg2)
}
