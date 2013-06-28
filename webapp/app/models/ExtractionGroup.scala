package models

case class ExtractionGroup(title: String, properScore: Double, instances: List[ExtractionInstance]) {
  def score = (properScore, instances.size)
}

object ExtractionGroup {
  def from(part: ExtractionPart, instances: List[ExtractionInstance]) = {
    // deduplicate and count instances
    val deduped = instances.groupBy(inst => Iterable(inst.arg1, inst.rel, inst.arg2s).mkString(" ").replaceAll("\\s+", "")).map { case (tuple, list) =>
      list.head.copy(count = list.size, ids = list.flatMap(_.ids))
    }

    // group instances by the grouping part
    deduped.groupBy(inst => part(inst) map (_.toLowerCase.replaceAll("\\s+", " "))).map { case (key, instances) =>
      val postags = part.postags(instances.head).split(" ")

      val properScore =
        if (postags.find(_ startsWith "NNP").isDefined) 1
        else if (postags.find(_ startsWith "PRP").isDefined) -1
        else 0

      ExtractionGroup(key.mkString("; "), properScore, instances.toList.sortBy(_.score(part)))
    }.toList.sorted(ExtractionGroupOrdering)
  }

  implicit object ExtractionGroupOrdering extends Ordering[ExtractionGroup] {
    def compare(a: ExtractionGroup, b: ExtractionGroup) =
      implicitly[Ordering[(Double, Int)]].reverse.compare(a.score, b.score)
  }
}
