package models

import java.util.ArrayList

import scala.collection.JavaConverters._

case class ExtractionInstance(arg1: String, rel: String, arg2s: Seq[String], arg1Types: Seq[String], relTypes: Seq[String], arg2Types: Seq[String], arg1_postag: String, rel_postag: String, arg2_postag: String, sentence: String, url: String, extractor: String, confidence: Double, count: Int) {
  def arg1s = Seq(arg1)
  def rels = Seq(rel)

  def arg1String = arg1
  def relString = rel
  def arg2String = arg2s.mkString("; ")

  val parts = Iterable(arg1, rel) ++ arg2s

  def arg1Postag = arg1_postag
  def relPostag = rel_postag
  def arg2Postag = arg2_postag

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

  def fromMap(map: Map[String, Any]) = {
    def toScalaSeq[T](arraylist: ArrayList[T]): Seq[T] = Option(arraylist).map(_.asScala.toSeq).getOrElse(Seq.empty)
    def optToScalaSeq[T](arraylist: Option[ArrayList[T]]): Seq[T] = arraylist.map(_.asScala.toSeq).getOrElse(Seq.empty)
    new ExtractionInstance(map("arg1").asInstanceOf[String], map("rel").asInstanceOf[String], toScalaSeq(map("arg2").asInstanceOf[ArrayList[String]]),
        optToScalaSeq(map.get("arg1_types").map(_.asInstanceOf[ArrayList[String]])), optToScalaSeq(map.get("rel_types").map(_.asInstanceOf[ArrayList[String]])), optToScalaSeq(map.get("arg2_types").map(_.asInstanceOf[ArrayList[String]])),
        map("arg1_postag").asInstanceOf[String], map("rel_postag").asInstanceOf[String], map("arg2_postag").asInstanceOf[String],
        map("sentence").asInstanceOf[String], map("url").asInstanceOf[String], map("extractor").asInstanceOf[String], map("confidence").asInstanceOf[Double], 1)
  }
}