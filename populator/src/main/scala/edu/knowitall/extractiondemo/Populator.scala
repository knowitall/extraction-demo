package edu.knowitall.extractiondemo

import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.common.SolrInputDocument

/**
 * A supertype for Solr Extraction populators.
 * S is the Sentence type, and should correspond to a line of input and contain necessary metadata such as DocID.
 * E is the Extracton type. 
 */
abstract class Populator[S, E] {
  
  def lineToSentence(line: String): S
  
  def extractSentence(sentence: S): ExtractionSource[S, E]
 
  def toSolrInputDocument(source: S)(extraction: E): SolrInputDocument
  
  def toSolrInputDocuments(esource: ExtractionSource[S, E]) = {
    esource.extractions map toSolrInputDocument(esource.source)
  }
}

abstract class SolrPopulator[S, E](val solrServer: SolrServer) extends Populator[S, E]

/**
 * A Wrapper for extractions and their source (sentence). Source should carry around any
 * metadata that came with the sentence, such as an ID or byte offsets, etc.
 */
abstract class ExtractionSource[S, E](val source: S, val extractions: Iterable[E])
