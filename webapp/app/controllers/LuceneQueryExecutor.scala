package controllers

import models.Query
import play.api.Logger
import models.ExtractionInstance
import play.api.Play.current

object LuceneQueryExecutor {
  val solrUrl = current.configuration.getString("db.solr.url").get
  def luceneQueryString(q: Query): String = {
    val strings =
      q.usedStrings.map { p => p.string.zipWithIndex.map
        { case (string, i) => "+" + p.part.short + ":%" + p.part.short + "_" + i + "%" }.mkString(" ")
      }
    val types =
      q.usedTypes.map { p => p.typ.zipWithIndex.map
        { case (typ, i) => "+" + p.part.short + "_types:%" + p.part.short + "_types_" + i + "%" }.mkString(" ")
      }
    val extractor = q.extractor match { case Some(ex) => " +extractor:%extractor%" case None => "" }

    (strings ++ types + extractor).mkString(" ")
  }

  def luceneQueryVariables(q: Query): Map[String, String] =
    Map.empty ++
       q.usedStrings.flatMap(part => part.string.zipWithIndex.map { case (string, i) => (part.part.short + "_" + i) -> part.part(q).string(i) }) ++
       q.usedTypes.flatMap(part => part.typ.zipWithIndex.map { case (typ, i) => (part.part.short + "_types_" + i) -> part.part(q).typ(i) }) ++
       q.extractor.map("extractor" -> _)

  def execute(q: Query) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = luceneQueryString(q)

    Logger.logger.debug("Lucene query: " + queryString)

    val result = client.query(queryString)
      .fields("arg1", "rel", "arg2", "sentence", "extractor")
      .sortBy(q.groupBy.short, Order.asc)
      .rows(1000)
      .getResultAs[ExtractionInstance](luceneQueryVariables(q))

    val list = result.documents.toList
    Logger.info("results received: " + list.size)
    list
  }

  def executeExact(arg1: String, rel: String, arg2: String) = {
    Logger.info("sentenes for: " + List(arg1, rel, arg2))
    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = "+arg1_exact:%arg1% +rel_exact:%rel% +arg2_exact:%arg2%"
    Logger.logger.debug("Lucene query: " + queryString)

    val result = client.query(queryString)
      .fields("arg1", "rel", "arg2", "sentence", "extractor")
      .rows(100)
      .getResultAs[ExtractionInstance](Map("arg1" -> arg1, "rel" -> rel, "arg2" -> arg2))

    val list = result.documents.toList
    Logger.info("sentence extractions received: " + list.size)
    list
  }
}