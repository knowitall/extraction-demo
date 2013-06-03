package controllers

import play.api.Play.current
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
import models.TypeHierarchy
import models.AdvancedQuery

object Application extends Controller {
  val typeHierarchy = {
    val typeHierarchyUrl = {
      val urlPath = "hierarchy"
      val url = Play.classloader.getResource(urlPath)
      require(url != null, "Could not find hierarchy: " + urlPath)
      url
    }
    TypeHierarchy.fromUrl(typeHierarchyUrl)
  }

  def decodeBasicAuth(auth: String) = {
    val baStr = auth.replaceFirst("Basic ", "")
    val Array(user, pass) = new String(new sun.misc.BASE64Decoder().decodeBuffer(baStr), "UTF-8").split(":")
    (user, pass)
  }

  case class User(name: String, password: String)
  object User {
    val users = Set(User("knowitall", "knowit!"), User("sri", "sanDi3goSol"), User("iarpa", "kay-deedee"))
    def find(username: String, password: String) = users.find (user => user.name == username && user.password == password)
  }

  def Authenticated[A](action: User => Action[A]): Action[A] = {
    def unauthorized = Unauthorized.withHeaders("WWW-Authenticate" -> "Basic realm=\"UW IARPA Realm\"")
    def getUser(request: RequestHeader): Option[User] = {
      def decodeBasicAuth(auth: String) = {
        val baStr = auth.replaceFirst("Basic ", "")
        val Array(user, pass) = new String(new sun.misc.BASE64Decoder().decodeBuffer(baStr), "UTF-8").split(":")
        (user, pass)
      }
      request.headers.get("Authorization").flatMap { auth =>
        val (name, password) = decodeBasicAuth(auth)
        User.find(name, password)
      }
    }

    // Wrap the original BodyParser with authentication
    val authenticatedBodyParser = parse.using { request =>
      getUser(request).map(u => action(u).parser).getOrElse {
        parse.error(unauthorized)
      }
    }

    // define the new Action
    Action(authenticatedBodyParser) { request =>
      getUser(request).map(u => action(u)(request)).getOrElse {
        unauthorized
      }
    }
  }

  def searchForm: Form[Query] = {
    import play.api.data.validation.Constraints._
    def unapply(query: Query): Option[(Option[String], Option[String], Option[String], Option[String], String)] = {
      Some((query.arg1.entryString, query.rel.entryString, query.arg2.entryString, query.extractor, query.groupBy.short))
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
        "groupBy" -> text)(apply)(unapply)) verifying ("All search fields cannot be empty", { query =>
          query.arg1.used || query.rel.used || query.arg2.used
        }))
  }
  def defaultSearchForm = searchForm.fill(Query(None, None, None))

  def advancedSearchForm: Form[AdvancedQuery] = {
    def unapply(query: AdvancedQuery): Option[(String, String)] = {
      Some(query.queryString, query.groupBy.short)
    }
    def apply(queryString: String, groupBy: String) = {
      AdvancedQuery(queryString, ExtractionPart.parse(groupBy))
    }
    import play.api.data.validation.Constraints._
    Form((mapping("query" -> text, "groupBy" -> text)(apply)(unapply)))
  }

  def index = Authenticated { user => Action {
    Ok(views.html.search(defaultSearchForm, advancedSearchForm))
  }}

  def submit = Authenticated { user => Action { implicit request =>
    searchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.search(errors, advancedSearchForm)),
      query => searchResult(query))
  }}

  def submitAdvanced = Authenticated { user => Action { implicit request =>
    advancedSearchForm.bindFromRequest.fold(
      errors => BadRequest(views.html.search(defaultSearchForm, errors)),
      query => searchResult(query))
  }}

  def searchResult(query: Query) = {
    val instances = LuceneQueryExecutor.execute(query)
    val queryString = LuceneQueryExecutor.luceneQueryVariables(query).foldLeft(
      LuceneQueryExecutor.luceneQueryString(query)) {
        case (query, (field, value)) =>
          query.replaceAll("%" + field + "%", "\"" + value + "\"")
      }
    val groups = ExtractionGroup.from(query.groupBy, instances)
    Ok(views.html.search(searchForm.fill(query), advancedSearchForm, Some(ResultSet(groups)), Some(queryString)))
  }

  def searchResult(query: AdvancedQuery) = {
    val queryString = query.queryString
    val instances = LuceneQueryExecutor.execute(queryString)
    val groups = ExtractionGroup.from(query.groupBy, instances).toList.sortBy(-_.instances.size)
    Ok(views.html.search(defaultSearchForm, advancedSearchForm.fill(query), Some(ResultSet(groups)), Some(queryString)))
  }

  def search(arg1: Option[String], rel: Option[String], arg2: Option[String]) = Action {
    val query = Query(arg1, rel, arg2)
    searchResult(query)
  }

  def sentences(arg1: String, rel: String, arg2s: Seq[String]) = Action {
    val extrs = LuceneQueryExecutor.executeExact(arg1, rel, arg2s)
    Ok(views.html.sentences(extrs))
  }
}
