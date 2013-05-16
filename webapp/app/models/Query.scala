package models

import scala.util.matching.Regex

case class Query(
    arg1: PartQuery,
    rel: PartQuery,
    arg2: PartQuery,
    extractor: Option[String],
    groupBy: ExtractionPart) {
  def used: Set[PartQuery] = {
    var set = Set.empty[PartQuery]

    if (arg1.used) set += arg1
    if (rel.used) set += rel
    if (arg2.used) set += arg2

    set
  }

  def usedStrings = used.filter(_.string.isDefined)
  def usedTypes = used.filter(_.typ.isDefined)
}

case class PartQuery(string: Option[String], typ: Option[String], part: ExtractionPart) {
  def entryString = (string, typ) match {
    case (Some(string), None) => Some(string)
    case (None, Some(typ)) => Some("[" + typ + "]")
    case (None, None) => None
    case _ => throw new IllegalArgumentException()
  }

  def used = string.isDefined || typ.isDefined
}
object PartQuery {
  val typeRegex = new Regex("""\s*\[(.*)\]\s*""")
  def fromEntry(entry: Option[String], part: ExtractionPart) = entry match {
    case Some(typeRegex(typ)) => PartQuery(None, Some(typ), part)
    case Some(string) => PartQuery(Some(string), None, part)
    case None => PartQuery(None, None, part)
  }
}

object Query {
  def empty = Query(None, None, None)

  def apply(
      arg1: Option[String],
      rel: Option[String],
      arg2: Option[String],
      extractor: Option[String] = None,
      groupBy: ExtractionPart = Argument1) = {

    val arg1Part = PartQuery.fromEntry(arg1, Argument1)
    val relPart = PartQuery.fromEntry(rel, Relation)
    val arg2Part = PartQuery.fromEntry(arg2, Argument2)

    new Query(arg1Part, relPart, arg2Part,
        extractor, groupBy)
  }

  val extractors = List("ReVerb", "R2A2", "Relnoun")
}