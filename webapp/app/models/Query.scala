package models

import scala.util.matching.Regex

case class Query(
    arg1: PartQuery,
    rel: PartQuery,
    arg2: PartQuery,
    extractor: Option[String],
    groupBy: ExtractionPart) {
  def used: Seq[PartQuery] = {
    var seq = Seq.empty[PartQuery]

    if (arg1.used) seq :+= arg1
    if (rel.used) seq :+= rel
    if (arg2.used) seq :+= arg2

    seq
  }

  def usedStrings = used.filter(!_.string.isEmpty)
  def usedTypes = used.filter(!_.typ.isEmpty)
}

case class PartQuery(string: Seq[String], typ: Seq[String], part: ExtractionPart) {
  def entryString =
    if (string.isEmpty && typ.isEmpty) None
    else Some((string ++ typ.map("[" + _ + "]")).mkString("; "))

  def used = !string.isEmpty || !typ.isEmpty
}
object PartQuery {
  val typeRegex = new Regex("""\s*\[(.*)\]\s*""")
  val empty = PartQuery(Seq.empty, Seq.empty, ExtractionPart.default)
  def fromEntry(entry: String, part: ExtractionPart) = {
    val (strings, types) =
      entry.split("""\s*;\s*""").foldLeft(List.empty[String], List.empty[String]) {
        case ((strings, types), entry) => entry match {
          case typeRegex(typ) => (strings, typ :: types)
          case string => (string :: strings, types)
        }
    }

    PartQuery(strings.toSeq, types.toSeq, part)
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

    val arg1Part = arg1.map(PartQuery.fromEntry(_, Argument1)).getOrElse(PartQuery.empty)
    val relPart = rel.map(PartQuery.fromEntry(_, Relation)).getOrElse(PartQuery.empty)
    val arg2Part = arg2.map(PartQuery.fromEntry(_, Argument2)).getOrElse(PartQuery.empty)

    new Query(arg1Part, relPart, arg2Part,
        extractor, groupBy)
  }

  val extractors = List("ReVerb", "R2A2", "Relnoun")
}