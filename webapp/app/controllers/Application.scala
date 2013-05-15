package controllers

import play.api._
import play.api.mvc._
import models.Query
import play.api.data.Form
import play.api.data.Forms.{ text, optional, mapping }
import org.apache.solr.client.solrj.SolrServer
import models.ExtractionInstance
import models.ResultSet
import models.ExtractionGroup
import models.Argument1
import models.ExtractionPart

object Application extends Controller {
  def searchForm: Form[Query] = {
    def unapply(query: Query): Option[(Option[String], Option[String], Option[String], Option[String], String)] = {
      Some(query.arg1.map(_.toString), query.rel.map(_.toString), query.arg2.map(_.toString), query.extractor, query.groupBy.short)
    }
    def apply(arg1: Option[String], rel: Option[String], arg2: Option[String], extractor: Option[String], groupBy: String) = {
      Query(arg1, rel, arg2, extractor, ExtractionPart.parse(groupBy))
    }
    Form(
      // Defines a mapping that will handle Contact values
      (mapping(
        "arg1" -> optional(text),
        "rel" -> optional(text),
        "arg2" -> optional(text),
        "extractor" -> optional(text),
        "groupBy" -> text)(apply)(unapply)).verifying("All search fields cannot be empty", { query =>
          query.arg1.isDefined || query.rel.isDefined || query.arg2.isDefined
        }))

  }

  def index = Action {
    Ok(views.html.search(searchForm))
  }

  def submit = Action { implicit request =>
    searchForm.bindFromRequest.fold(
        errors => BadRequest(views.html.search(searchForm)),
        query => searchResult(query))
  }

  def searchResult(query: Query) = {
    val instances = executeQuery(query)
    val groups = ExtractionGroup.from(query.groupBy, instances).toList
    Ok(views.html.search(searchForm.fill(query), Some(ResultSet(groups))))
  }

  def search(arg1: Option[String], rel: Option[String], arg2: Option[String]) = Action {
    val query = Query(arg1, rel, arg2)
    searchResult(query)
  }

  def executeQuery(q: Query) = {
    Logger.info("query for: " + q)

    import jp.sf.amateras.solr.scala._

    val client = new SolrClient("http://localhost:8983/solr")

    val queryString = q.used.map(p => "+" + p.short + ": %" + p.short + "%").mkString(" ") +
      (q.extractor match { case Some(ex) => " +extractor:%extractor%" case None => "" })
    Logger.logger.debug("Lucene query: " + queryString)

    val result = client.query(queryString)
      .fields("arg1", "rel", "arg2", "extractor")
      .sortBy(q.groupBy.short, Order.asc)
      .rows(1000)
      .getResultAs[ExtractionInstance](Map.empty ++ q.used.map(p => p.short -> p(q).get) ++ q.extractor.map("extractor" -> _))

    val list = result.documents.toList
    Logger.info("results received: " + list.size)
    list
  }
}
