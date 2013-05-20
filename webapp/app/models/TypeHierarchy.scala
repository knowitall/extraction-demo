package models

import edu.knowitall.collection.immutable.graph.Graph
import edu.knowitall.common.Resource
import scala.io.Source
import java.io.File
import edu.knowitall.collection.immutable.graph.Graph.Edge
import java.net.URL

class TypeHierarchy(graph: Graph[String]) {
  def baseTypes(typ: String): Set[String] = {
    val lc = typ.toLowerCase
    if (graph.vertices contains lc) {
      // return leaves of graph
      graph.inferiors(lc).filter { vertex =>
        graph.successors(vertex).isEmpty
      }
    }
    else {
      Set(typ)
    }
  }
}

object TypeHierarchy {
  def fromUrl(url: URL) = {
    val graph = Resource.using(Source.fromInputStream(url.openStream())) { source =>
      val edges = source.getLines.map(_.split("\t")).toList.map {
        case Array(source, dest) => new Edge(source.toLowerCase, dest.toLowerCase, "edge")
      }

      new Graph[String](edges)
    }

    new TypeHierarchy(graph)
  }
}