package models

case class ExtractionGroup(title: String, instances: List[ExtractionInstance])

object ExtractionGroup {
  def from(part: ExtractionPart, instances: List[ExtractionInstance]) = {
    val deduped = instances.groupBy(inst => (inst.arg1, inst.rel, inst.arg2)).map { case(tuple, list) =>
      list.head.copy(count = list.size)
    }
    deduped.groupBy(part.apply).map { case (key, instances) =>
      ExtractionGroup(key, instances.toList.sortBy(-_.count))
    }
  }
}
