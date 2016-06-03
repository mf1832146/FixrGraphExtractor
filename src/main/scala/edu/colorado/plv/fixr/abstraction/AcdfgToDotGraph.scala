package edu.colorado.plv.fixr.abstraction

import java.util
import java.util.Iterator

import edu.colorado.plv.fixr.graphs.CFGToDotGraph.{DotNamer, NodeComparator}
import edu.colorado.plv.fixr.graphs.{UnitCdfgGraph, CFGToDotGraph}
import soot.{Body, Unit, Local}
import soot.util.dot.{DotGraphEdge, DotGraphNode, DotGraphConstants, DotGraph}


/**
 * AcdfgToDotGraph
 *   Class implementing conversion from abstract control data flow graph (ACDFG)
 *   to .dot graph format.
 *
 *   @author Rhys Braginton Pettee Olsen <rhol9958@colorado.edu>
 *   @group  University of Colorado at Boulder CUPLV
 */

class AcdfgToDotGraph(acdfg : Acdfg) extends CFGToDotGraph {
  def draw() : DotGraph = {
		var canvas     : DotGraph       = initDotGraph(null)
    canvas.setGraphLabel("ACDFG")
    import scala.collection.JavaConversions._
    for (n <- acdfg.nodes) {
      var dotNode : DotGraphNode = canvas.drawNode(n._1.toString)
      n match {
        case n@(id : Long, node : acdfg.DataNode) =>
          dotNode.setLabel("#" + id.toString + ": " + node.datatype.toString + " " + node.name)
          dotNode.setStyle(DotGraphConstants.NODE_STYLE_DASHED)
          dotNode.setAttribute("shape", "ellipse")
        case n@(id : Long, node : acdfg.MethodNode) =>
          var name : String = "#" + node.id.toString + ": "
          if (node.assignee.nonEmpty) {
            name += (node.assignee.get + " = ")
          }
          val arguments = node.argumentNames.zip(node.argumentIds).map { case (name, id) =>
            if (id == 0) {
              name // + " [string constant]"
            } else {
              name + " [#" + id.toString + "]"
            }
          }
          name += node.name
          if (node.invokee.isDefined) {
            name += "[#" + node.invokee.get + "]"
          }
          name += "(" + arguments.mkString(",") + ")"
          dotNode.setLabel(name)
        case n@(id : Long, node : acdfg.MiscNode) =>
          dotNode.setLabel("#" + id.toString)
        case n => Nil
      }
    }
    for (e <- acdfg.edges) {
      var dotEdge : DotGraphEdge = canvas.drawEdge(e._2.from.toString, e._2.to.toString)
      e match {
        case e@(id : Long, edge : acdfg.DefEdge) =>
          dotEdge.setAttribute("color", "blue")
        case e@(id : Long, edge : acdfg.UseEdge) =>
          dotEdge.setAttribute("color", "red")
          dotEdge.setAttribute("Damping", "0.7")
        case _ => null
      }
    }
    canvas
	}
}
