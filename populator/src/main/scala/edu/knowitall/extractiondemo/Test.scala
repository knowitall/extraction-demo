package edu.knowitall.extractiondemo

import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.chunkedextractor.ReVerb
import edu.washington.cs.knowitall.extractor.ReVerbExtractor
import edu.washington.cs.knowitall.nlp.OpenNlpSentenceChunker

object Test extends App {
  val chunker2 = new OpenNlpSentenceChunker()
  val chunker = new OpenNlpChunker()
  val chunked1 = chunker("Michael ran down the hill.")
  val chunked2 = chunker2.chunkSentence("Michael ran down the hill.")
  val reverb = new ReVerbExtractor()

  println("Initialized")
  for {
      group <- Iterator.continually(chunked2).grouped(10000)
      chunked <- group.par
      } {
    try {
      reverb.extract(chunked)
    } catch {
      case e => e.printStackTrace(); System.exit(1)
    }
  }
}