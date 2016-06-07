package edu.colorado.plv.fixr.extractors

/**
 * Options used in the ACDFG extraction
 * 
 * @author Sergio Mover
 */
class ExtractorOptions {
  // Soot options
  var sootClassPath : String = null;
  var readFromJimple : Boolean = true;

  var sliceFilter : String = null;
  
  //  
  var processDir : List[String] = null;
  var className : String = null;
  var methodName : String = null;
   
  //
  var outputDir : String = null;
  var provenanceDir : String = null;
}