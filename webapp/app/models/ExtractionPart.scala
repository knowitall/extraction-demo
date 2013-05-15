package models

sealed abstract class ExtractionPart(val name: String, val short: String) {
  def apply(instance: ExtractionInstance): String
  def apply(query: Query): Option[String]
}
object ExtractionPart {
  def parse(string: String) = string match {
    case "arg1" => Argument1
    case "rel" => Relation
    case "arg2" => Argument2
  }

  def parts = List(Argument1, Relation, Argument2)
}
case object Argument1 extends ExtractionPart("Argument 1", "arg1") {
  def apply(instance: ExtractionInstance): String = instance.arg1
  def apply(query: Query): Option[String] = query.arg1
}
case object Relation extends ExtractionPart("Relation", "rel") {
  def apply(instance: ExtractionInstance): String = instance.rel
  def apply(query: Query): Option[String] = query.rel
}
case object Argument2 extends ExtractionPart("Argument 2", "arg2") {
  def apply(instance: ExtractionInstance): String = instance.arg2
  def apply(query: Query): Option[String] = query.arg2
}
