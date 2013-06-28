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

  def fullLuceneQueryString(query: Query): String = {
    LuceneQueryExecutor.luceneQueryVariables(query).foldLeft(
      LuceneQueryExecutor.luceneQueryString(query)) {
        case (query, (field, value)) =>
          query.replaceAll("%" + field + "%", "\"" + value + "\"")
      }
  }

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
    val corpus = q.corpus match { case Some(ex) => " +corpus:%corpus%" case None => "" }

    (strings ++ types :+ extractor :+ corpus).mkString(" ")
  }

  def luceneQueryVariables(q: Query): Map[String, String] =
    Map.empty ++
       q.usedStrings.flatMap(part => part.string.zipWithIndex.map { case (string, i) => (part.part.short + "_" + i) -> part.part(q).string(i) }) ++
       q.usedTypes.flatMap(part => part.typ.flatMap(Application.typeHierarchy.baseTypes).zipWithIndex.map { case (typ, i) => (part.part.short + "_types_" + i) -> typ }) ++
       q.extractor.map("extractor" -> _) ++
       q.corpus.map("corpus" -> _)

  def execute(q: Query) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = luceneQueryString(q)
    val queryVariables = luceneQueryVariables(q)

    Logger.logger.debug("Lucene query: " + queryString)
    Logger.logger.debug("Lucene variables: " + queryVariables)

    val result = client.query(queryString)
      .fields("id", "arg1", "rel", "arg2", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "corpus", "confidence")
      .rows(10000)
      .getResultAsMap(queryVariables)

    val list = result.documents.toList
    Logger.info("results received: " + list.size)
    list.map(ExtractionInstance.fromMap)
  }

  def executeExact(arg1: String, rel: String, arg2s: Seq[String]) = {
    val arg2 = arg2s.head

    Logger.info("sentenes for: " + List(arg1, rel, arg2))
    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = "+arg1_exact:%arg1% +rel_exact:%rel% +arg2_exact:%arg2%"
    Logger.logger.debug("Lucene query: " + queryString)
    Logger.logger.debug("Lucene variables: " + (List("arg1", "rel", "arg2") zip List(arg1, rel, arg2)))

    val result = client.query(queryString)
      .fields("id", "arg1", "rel", "arg2", "arg1_types", "rel_types", "arg2_types", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
      .getResultAsMap(Map("arg1" -> arg1, "rel" -> rel, "arg2" -> arg2))

    val list = result.documents.toList
    Logger.info("sentence extractions received: " + list.size)
    list.map(ExtractionInstance.fromMap)
  }

  def executeIds(ids: Seq[String]) = {
    Logger.info("sentenes for: " + ids)
    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    val queryString = ids.zipWithIndex.map { case (id, i) => s"id:%id$i%" }.mkString(" OR ")
    val queryVariables = ids.zipWithIndex.map { case (id, i) => s"id$i" -> id }.toMap
    Logger.logger.debug("Lucene query: " + queryString)
    Logger.logger.debug("Lucene variables: " + queryVariables)

    val result = client.query(queryString)
      .fields("id", "arg1", "rel", "arg2", "arg1_types", "rel_types", "arg2_types", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
      .getResultAsMap(queryVariables)

    val list = result.documents.toList
    Logger.info("sentence extractions received: " + list.size)
    list.map(ExtractionInstance.fromMap)
  }

  def execute(q: String) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient(solrUrl)

    Logger.logger.debug("Lucene query: " + q)

    val result = client.query(q)
      .fields("id", "arg1", "rel", "arg2", "arg1_postag", "rel_postag", "arg2_postag", "sentence", "url", "extractor", "confidence")
      .rows(10000)
      .getResultAsMap()

    val list = result.documents.toList
    Logger.info("results received: " + list.size)
    list.map(ExtractionInstance.fromMap)
  }
}
