package controllers

import models.Query
import play.api.Logger
import models.ExtractionInstance

object LuceneQueryExecutor {
  def luceneQueryString(q: Query): String = {
    Iterable(q.usedStrings.map
      { p => "+" + p.part.short + ":%" + p.part.short + "%"}.mkString(" "),
      q.usedTypes.map
      { p => "+" + p.part.short + "_types:%" + p.part.short + "_types%"}.mkString(" "),
      (q.extractor match { case Some(ex) => " +extractor:%extractor%" case None => "" })).mkString(" ")
  }

  def luceneQueryVariables(q: Query): Map[String, String] = {
    (Map.empty ++ q.used.map(part =>
      (part.string, part.typ) match {
        case (Some(string), None) => (part.part.short -> part.part(q).string.get)
        case (None, Some(typ)) => (part.part.short + "_types" -> part.part(q).typ.get)
        case _ => throw new IllegalArgumentException()
      }) ++ q.extractor.map("extractor" -> _))
  }

  def execute(q: Query) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient("http://ahab.cs.washington.edu:8983/solr")

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

    val client = new SolrClient("http://ahab.cs.washington.edu:8983/solr")

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