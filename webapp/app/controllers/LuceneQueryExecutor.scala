package controllers

import models.Query
import play.api.Logger
import models.ExtractionInstance
import play.api.Play.current
import play.api.Play
import models.TypeHierarchy
import java.util.concurrent.atomic.AtomicInteger

object LuceneQueryExecutor {
  val solrUrl = current.configuration.getString("db.solr.url").get
  Logger.info("SOLR Url: " + solrUrl)

  def luceneQueryString(q: Query): String = {
    val strings =
      q.usedStrings.map { p => p.string.zipWithIndex.map
        { case (string, i) => "+" + p.part.short + ":%" + p.part.short + "_" + i + "%" }.mkString(" ")
      }
    val i = new AtomicInteger(0)
    val types =
      q.usedTypes.flatMap { p => p.typ.map { typ =>
        "+(" + Application.typeHierarchy.baseTypes(typ).map { case typ => p.part.short + "_types:%" + p.part.short + "_types_" + i.getAndIncrement() + "%" }.mkString(" OR ") + ")"
      }}
    val extractor = q.extractor match { case Some(ex) => " +extractor:%extractor%" case None => "" }

    (strings ++ types :+ extractor).mkString(" ")
  }

  def luceneQueryVariables(q: Query): Map[String, String] =
    Map.empty ++
       q.usedStrings.flatMap(part => part.string.zipWithIndex.map { case (string, i) => (part.part.short + "_" + i) -> part.part(q).string(i) }) ++
       q.usedTypes.flatMap(part => part.typ.flatMap(Application.typeHierarchy.baseTypes).zipWithIndex.map { case (typ, i) => (part.part.short + "_types_" + i) -> typ }) ++
       q.extractor.map("extractor" -> _)

  def execute(q: Query) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = luceneQueryString(q)

    Logger.logger.debug("Lucene query: " + queryString)

    val result = client.query(queryString)
      .fields("arg1", "rel", "arg2", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
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
      .fields("arg1", "rel", "arg2", "arg1_types", "rel_types", "arg2_types", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
      .getResultAs[ExtractionInstance](Map("arg1" -> arg1, "rel" -> rel, "arg2" -> arg2))

    val list = result.documents.toList
    Logger.info("sentence extractions received: " + list.size)
    list
  }

  def execute(q: String) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    Logger.logger.debug("Lucene query: " + q)

    val result = client.query(q)
      .fields("arg1", "rel", "arg2", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
      .getResultAs[ExtractionInstance]()

    val list = result.documents.toList
    Logger.info("results received: " + list.size)
    list
  }
}