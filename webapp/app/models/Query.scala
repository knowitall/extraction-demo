package models

case class Query(arg1: Option[String], rel: Option[String], arg2: Option[String], extractor: Option[String] = None, groupBy: ExtractionPart = Argument1) {
  def used: Set[ExtractionPart] = {
    var set = Set.empty[ExtractionPart]

    if (arg1.isDefined) set += Argument1
    if (rel.isDefined) set += Relation
    if (arg2.isDefined) set += Argument2

    set
  }

  // def free: Set[ExtractionPart] = Set(Argument1, Relation, Argument2) -- used
}

object Query {
  def empty = Query(None, None, None)

  val extractors = List("ReVerb", "R2A2", "Relnoun")
}