package models

sealed abstract class ExtractionPart(val name: String, val short: String) {
  def apply(instance: ExtractionInstance): Seq[String]
  def apply(query: Query): PartQuery
}
object ExtractionPart {
  def parse(string: String) = string match {
    case "arg1" => Argument1
    case "rel" => Relation
    case "arg2" => Argument2
  }

  val default = Argument1

  def parts = List(Argument1, Relation, Argument2)
}
case object Argument1 extends ExtractionPart("Argument 1", "arg1") {
  def postags(instance: ExtractionInstance): String = instance.arg1Postag
  def apply(instance: ExtractionInstance): Seq[String] = instance.arg1s
  def apply(query: Query): PartQuery = query.arg1
}
case object Relation extends ExtractionPart("Relation", "rel") {
  def postags(instance: ExtractionInstance): String = instance.relPostag
  def apply(instance: ExtractionInstance): Seq[String] = instance.rels
  def apply(query: Query): PartQuery = query.rel
}
case object Argument2 extends ExtractionPart("Argument 2", "arg2") {
  def postags(instance: ExtractionInstance): String = instance.arg2Postag
  def apply(instance: ExtractionInstance): Seq[String] = instance.arg2s
  def apply(query: Query): PartQuery = query.arg2
}
