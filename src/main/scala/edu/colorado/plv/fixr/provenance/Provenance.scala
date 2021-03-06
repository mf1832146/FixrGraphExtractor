package edu.colorado.plv.fixr.provenance

import java.io.{ByteArrayOutputStream, OutputStream}

import com.google.googlejavaformat.java.Formatter
import edu.colorado.plv.fixr.abstraction.{Acdfg, AcdfgToDotGraph}
import edu.colorado.plv.fixr.graphs.{CDFGToDotGraph, UnitCdfgGraph}

import scalatags.Text.TypedTag
import scalatags.Text.all._
import soot.{Body, SootMethod, SootClass}
import soot.toolkits.graph.DirectedGraph
import soot.util.cfgcmd.CFGToDotGraph

import scalatags.stylesheet._

class Provenance(
  source : String,
  body : Body,
  slicedBodyOption : Option[Body],
  prefix : String,
  cfgOption : Option[DirectedGraph[_]],
  cdfg : UnitCdfgGraph,
  acdfg : Acdfg
) {
  def toHtml = {
    val ghr = acdfg.getGitHubRecord
    val sourceInfo = acdfg.getSourceInfo

    var packageName : String = sourceInfo.packageName
    var className : String = sourceInfo.className
    var methodName : String  = sourceInfo.methodName
    var methodLine : Int  = sourceInfo.methodLineNumber
    var fileName : String  = sourceInfo.sourceClassName

    try {
      val sootMethod = body.getMethod()
      val sootClass = sootMethod.getDeclaringClass()
      /* Fallback to body if tags are not present */
      if (className == "") {
        className = sootClass.getName()
      }
      if (methodName == "") methodName = sootMethod.getName()
    } catch {
      case _ : Throwable => ()
    }

    val fullyQualifiedName = s"${className}.${methodName}"

    val gitPretty = s"${ghr.userName}/${ghr.repoName}/${ghr.commitHash}"
    val gitUrl = s"http://github.com/${ghr.userName}/${ghr.repoName}/tree/${ghr.commitHash}"
    val gitFindUrl = s"${ghr.url}/find/${ghr.commitHash}"
    val sliceStr =
      slicedBodyOption match {
        case None => "Body was not sliced"
        case Some(slicedBody) => slicedBody.toString
      }

    scalatags.Text.all.html(
      scalatags.Text.all.head(
        scalatags.Text.all.script(src:="https://github.com/mdaines/viz.js/releases/download/v1.3.0/viz.js"),
        scalatags.Text.all.script(s"""
          var acdfgReq = new XMLHttpRequest();
          acdfgReq.open('GET', '${prefix}.acdfg.dot', true);
          acdfgReq.send();
          acdfgReq.onload = function() {
            var acdfgText = acdfgReq.responseText;
            var acdfgContainer = document.getElementById('acdfg');
            var acdfg = Viz(acdfgText, options={ format: 'svg', engine: 'dot' });
            acdfgContainer.innerHTML = acdfg;
          };
          """
        )
      ),
      scalatags.Text.all.body(
        scalatags.Text.all.h1(fullyQualifiedName),
        scalatags.Text.all.div(
          scalatags.Text.all.p(
            "Repository: ",
            scalatags.Text.all.a(href := gitUrl)(gitPretty)
          ),
          scalatags.Text.all.p(s"File (line: ${methodLine}): ${fileName}",
            scalatags.Text.all.a(href := gitFindUrl)(" (Find)")),
          scalatags.Text.all.p("Package: ", packageName),
          scalatags.Text.all.p("Class: ", className),
          scalatags.Text.all.p(s"Method: ${methodName}")
        ),

        //
        scalatags.Text.all.h2("Abstract Control Data Flow Graph (ACDFG)"),
        // scalatags.Text.all.img(src := prefix + ".acdfg.svg")
        scalatags.Text.all.div(id := "acdfg",""),

        scalatags.Text.all.h2("Debug informations"),
        scalatags.Text.all.div(
          scalatags.Text.all.h3("Jimple"),
          scalatags.Text.all.pre(scalatags.Text.all.code(body.toString)),
          scalatags.Text.all.h3("Sliced Jimple"),
          scalatags.Text.all.pre(scalatags.Text.all.code(sliceStr)),
          scalatags.Text.all.h3("Control Flow Graph (CFG)"),
          scalatags.Text.all.div(id := "cfg",
            scalatags.Text.all.img(src := prefix + ".cfg.svg")
          ),
          scalatags.Text.all.h3("Control Data Flow Graph (CDFG)"),
          scalatags.Text.all.div(id := "cdfg",
            scalatags.Text.all.img(src := prefix + ".cdfg.svg")
          ),
          // Footer
          scalatags.Text.all.p(scalatags.Text.all.em(
            "University of Colorado at Boulder"
          )),
          scalatags.Text.all.p(scalatags.Text.all.em(
            "CUPLV Lab"
          )),
          scalatags.Text.all.p(scalatags.Text.all.em(
            "DARPA MUSE Round II, Fixr 2016"
          )),
          scalatags.Text.all.p(scalatags.Text.all.em(
            "Sergio Mover, Rhys Braginton Pettee Olsen, Sriram Sankaranarayanan (PI)"
          ))
        ) // end of debug div
      )
    )
  }
}
