extraction-demo
===============

A project for creating extractions from a list of sentences and providing a demo for exploring Open IE extractions.
The primary purpose for this project is for exploration of Open IE in the IARPA project.

## Processing Data

The `populator` project is responsible for building a new SOLR index.  The `SrlSentenceProcessor` project takes
sentences as input and parses those sentences.  This is the most time-consuming part of the pipeline.
`SolrExtractionPopulator` runs over these parsed sentences, chunks them, runs various extractors (specified in
`ExtractionPopulator` and sends them to the specified [SOLR](http://lucene.apache.org/solr/) database.

If you receive raw data that is not segmented into sentences, you may need to run them through [some sentence
segmentation code](https://github.com/schmmd/textconverter) first.

There is a KDD SOLR instance running at http://trusty.cs.washington.edu:8983/.  The SOLR schema is also in
this repository in `/extra`.

## Running the demo

The `webapp` project runs the demo.  It needs the SOLR database configured in `conf/application.conf`.
