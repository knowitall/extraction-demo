package models

import java.util.ArrayList

import scala.collection.JavaConverters._

case class ExtractionInstance(arg1: String, rel: String, arg2: ArrayList[String], arg1_types: ArrayList[String], rel_types: ArrayList[String], arg2_types: ArrayList[String], arg1_postag: String, rel_postag: String, arg2_postag: String, sentence: String, url: String, extractor: String, confidence: Double, count: Int) {
  def arg1s = Seq(arg1)
  def rels = Seq(rel)
  val arg2Seq = Option(arg2).map(_.asScala.toSeq).getOrElse(Seq.empty)

  def arg1String = arg1
  def relString = rel
  def arg2String = arg2Seq.mkString("; ")

  val parts = Iterable(arg1, rel) ++ arg2Seq

  def arg1Postag = arg1_postag
  def relPostag = rel_postag
  def arg2Postag = arg2_postag

  def arg1Types = Option(arg1_types).map(_.asScala.toSeq).getOrElse(Seq.empty)
  def relTypes = Option(rel_types).map(_.asScala.toSeq).getOrElse(Seq.empty)
  def arg2Types = Option(arg2_types).map(_.asScala.toSeq).getOrElse(Seq.empty)

  def text(groupBy: ExtractionPart) = {
    (groupBy match {
      case Argument1 => Seq(this.relString, this.arg2String)
      case Relation => Seq(this.arg1String, this.arg2String)
      case Argument2 => Seq(this.arg1String, this.relString)
    }).mkString(" ")
  }

  def score(groupBy: ExtractionPart) = {
    val postags = groupBy match {
      case Argument1 => Seq(this.arg2Postag.split(" "))
      case Relation => Seq(this.arg1Postag.split(" "), this.arg2Postag.split(" "))
      case Argument2 => Seq(this.arg1Postag.split(" "))
    }
    val properScore = postags.map { postags =>
      if (postags.find(_ startsWith "NNP").isDefined) 1
      else if (postags.find(_ startsWith "PRP").isDefined) -1
      else 0
    }.sum

    (-properScore, -this.confidence)
  }
}

object ExtractionInstance {
  class ExtractionInstanceOrdering(groupBy: ExtractionPart) extends Ordering[ExtractionInstance] {
    def compare(a: ExtractionInstance, b: ExtractionInstance) =
      implicitly[Ordering[(Int, Double)]].compare(a.score(groupBy), b.score(groupBy))
  }
}
