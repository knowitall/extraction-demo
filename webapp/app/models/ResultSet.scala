package models

case class ResultSet(groups: List[ExtractionGroup]) {
  def instanceCount = groups.map(group => group.instances.size).sum
}
