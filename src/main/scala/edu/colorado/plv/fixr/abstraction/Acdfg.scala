package edu.colorado.plv.fixr.abstraction

import edu.colorado.plv.fixr.graphs.UnitCdfgGraph
import soot.jimple.{IdentityStmt, AssignStmt, InvokeStmt}
import soot.jimple.internal.JimpleLocal
import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable.ArrayBuffer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import edu.colorado.plv.fixr.protobuf.ProtoAcdfg
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters._

/**
  * Acdfg
  *   Class implementing abstract control data flow graph (ACDFG)
  *
  *   @author Rhys Braginton Pettee Olsen <rhol9958@colorado.edu>
  *   @group  University of Colorado at Boulder CUPLV
  */

abstract class Node {
  def id : Long
  override def toString = this.getClass.getSimpleName + "(" + id.toString + ")"
}

abstract class CommandNode extends Node {
  override def toString = this match {
    case n : MethodNode =>
      n.getClass.getSimpleName + "(" +
        "id: "+ n.id.toString + ", " +
        "invokee: "   + n.invokee.toString + ", " +
        "name: "      + n.name.toString + ", " +
        "arguments: [" +
        n.argumentIds.map(_.toString).mkString(", ") +
        "]" +
        ")"
    case n =>
      this.getClass.getSimpleName + "(" + id.toString + ")"
  }
}

case class DataNode(
  override val id : Long,
  name : String,
  datatype : String
) extends Node

case class MethodNode(
  override val id : Long,
  //  note: assignee is NOT used to generate Protobuf
  // assignee : Option[String],
  invokee : Option[Long],
  name : String,
  argumentIds : Vector[Long]
  // var argumentNames : Array[String]
) extends CommandNode

case class MiscNode(
  override val id : Long
) extends CommandNode

/* Edges */

abstract class Edge {
  val to   : Long
  val from : Long
  val id   : Long
  override def toString =
    this.getClass.getSimpleName +
      "(id: "     + id.toString   +
      ", to: "    + to.toString   +
      ", from: "  + from.toString +
      ")"
}

case class DefEdge(
  override val id   : Long,
  override val from : Long,
  override val to   : Long
) extends Edge

case class UseEdge(
                    override val id   : Long,
                    override val from : Long,
                    override val to   : Long
                  ) extends Edge

case class ControlEdge(
                        override val id   : Long,
                        override val from : Long,
                        override val to   : Long
                      ) extends Edge

case class AdjacencyList(Nodes : Vector[Node], Edges : Vector[Edge])

class Acdfg(
  adjacencyList: AdjacencyList,
  cdfg : UnitCdfgGraph,
  protobuf : ProtoAcdfg.Acdfg
) {

  val logger : Logger = LoggerFactory.getLogger(classOf[Acdfg])

  /* Nodes */

  // ControlEdge, ExceptionalEdge

  // var edges : ArrayBuffer[Edge] = ArrayBuffer()
  //var nodes : ArrayBuffer[Node] = ArrayBuffer()

  /*
   * Edges and nodes
   *   Design rationale: our graph will be very sparse; want indexing by ID to be fast
   */

  protected[fixr] var edges = scala.collection.mutable.HashMap[Long, Edge]()
  protected[fixr] var nodes = scala.collection.mutable.HashMap[Long, Node]()

  /* Internal Protobuf value generated as needed */
  private lazy val pb : ProtoAcdfg.Acdfg = {
    var builder : ProtoAcdfg.Acdfg.Builder = ProtoAcdfg.Acdfg.newBuilder()
    edges.foreach {
      case (id : Long, edge : ControlEdge) =>
        val protoControlEdge: ProtoAcdfg.Acdfg.ControlEdge.Builder =
          ProtoAcdfg.Acdfg.ControlEdge.newBuilder()
        protoControlEdge.setId(id)
        protoControlEdge.setFrom(edge.from)
        protoControlEdge.setTo(edge.to)
        builder.addControlEdge(protoControlEdge)
      case (id : Long, edge : DefEdge) =>
        val protoDefEdge: ProtoAcdfg.Acdfg.DefEdge.Builder =
          ProtoAcdfg.Acdfg.DefEdge.newBuilder()
        protoDefEdge.setId(id)
        protoDefEdge.setFrom(edge.from)
        protoDefEdge.setTo(edge.to)
        builder.addDefEdge(protoDefEdge)
      case (id : Long, edge : UseEdge) =>
        val protoUseEdge: ProtoAcdfg.Acdfg.UseEdge.Builder =
          ProtoAcdfg.Acdfg.UseEdge.newBuilder()
        protoUseEdge.setId(id)
        protoUseEdge.setFrom(edge.from)
        protoUseEdge.setTo(edge.to)
        builder.addUseEdge(protoUseEdge)
    }
    nodes.foreach {
      case (id : Long, node : DataNode) =>
        val protoDataNode : ProtoAcdfg.Acdfg.DataNode.Builder =
          ProtoAcdfg.Acdfg.DataNode.newBuilder()
        protoDataNode.setId(id)
        protoDataNode.setName(node.name)
        protoDataNode.setType(node.datatype)
        builder.addDataNode(protoDataNode)
      case (id : Long, node : MiscNode) =>
        val protoMiscNode : ProtoAcdfg.Acdfg.MiscNode.Builder =
          ProtoAcdfg.Acdfg.MiscNode.newBuilder()
        protoMiscNode.setId(id)
        builder.addMiscNode(protoMiscNode)
      case (id : Long, node : MethodNode) =>
        val protoMethodNode : ProtoAcdfg.Acdfg.MethodNode.Builder =
          ProtoAcdfg.Acdfg.MethodNode.newBuilder()
        protoMethodNode.setId(id)
        if (node.invokee.isDefined) {
          protoMethodNode.setInvokee(node.invokee.get)
        }
        node.argumentIds.foreach(protoMethodNode.addArgument)
        protoMethodNode.setName(node.name)
        builder.addMethodNode(protoMethodNode)
    }
    builder.build()
  }

  object MinOrder extends Ordering[Int] {
    def compare(x:Int, y:Int) = y compare x
  }

  var freshIds = new scala.collection.mutable.PriorityQueue[Long]().+=(0)

  def toAdjacencyList : AdjacencyList =
    AdjacencyList(nodes.values.toVector,edges.values.toVector)

  def union(that : Acdfg) : AdjacencyList =
    AdjacencyList(
      (this.nodes.values.toSet | this.nodes.values.toSet).toVector,
      (this.edges.values.toSet | this.edges.values.toSet).toVector
    )
  def |(that : Acdfg) = union(that)

  def disjointUnion(that : Acdfg) : AdjacencyList =
    AdjacencyList(
      ((this.nodes.values.toSet &~ that.nodes.values.toSet) ++
      (that.nodes.values.toSet -- this.nodes.values.toSet)).toVector,
     ((this.edges.values.toSet -- that.edges.values.toSet) ++
      (that.edges.values.toSet -- this.edges.values.toSet)).toVector
    )
  def +|(that : Acdfg) = disjointUnion(that)

  def intersection(that : Acdfg) : AdjacencyList =
    AdjacencyList(
      (this.nodes.values.toSet | this.nodes.values.toSet).toVector,
      (this.edges.values.toSet | this.edges.values.toSet).toVector
    )
  def &(that : Acdfg) = intersection(that)

  def diff(that : Acdfg) : AdjacencyList =
    AdjacencyList(
      (this.nodes.values.toSet -- this.nodes.values.toSet).toVector,
      (this.edges.values.toSet -- this.edges.values.toSet).toVector
    )
  def --(that : Acdfg) = diff(that)


  def equals(that : Acdfg) : Boolean = {
    val du = this +| that
    du.Nodes.isEmpty && du.Edges.isEmpty
  }

  def ==(that : Acdfg) : Boolean =
    if (that != null) this.equals(that) else false

  /*
   * Methods to access ids while maintaining HashMaps, RB tree correctness
   */

  def getNewId : Long = {
    val newId = freshIds.dequeue()
    // Would be nice to do:
    //   whenever freshIds is empty, add next 100 new ids instead of just the next 1
    if (freshIds.isEmpty) {
      // Must maintain invariant: freshIds always has at least one fresh id
      freshIds.enqueue(newId + 1)
    }
    // System.err.logger.info("ID #" + newId.toString + " issued")
    newId
  }

  def removeId(id : Long) = {
    freshIds.enqueue(id)
    // System.err.logger.info("ID #" + id.toString + " revoked")
  }

  def addNode(id : Long, node : Node) : (Long, Node) = {
    val oldCount = nodes.size
    nodes.+=((id, node))
    val newCount = nodes.size
    assert(oldCount + 1 == newCount)
    (id, node)
  }

  def removeEdge(to : Long, from : Long) = {
    val id = edges.find {pair => (pair._2.from == from) && (pair._2.to == to) }.get._1
    edges.remove(id)
  }

  def removeEdgesOf(id : Long) = {
    edges.find({pair => (pair._2.from == id) || pair._2.to == id }).foreach(pair => edges remove pair._1)
  }

  def removeDataNode(name : String) = {
    nodes.find(pair => pair._2.isInstanceOf[DataNode]).foreach(
      pair => {
        if (pair._2.asInstanceOf[DataNode].name == name) {
          nodes.remove(pair._1)
        }
      }
    )
  }

  def removeNode(id : Long) = {
    nodes.remove(id)
    removeId(id)
  }

  override def toString = {
    var output : String = "ACDFG:" + "\n"
    output += ("  " + "Nodes:\n")
    nodes.foreach(node => output += ("    " + node.toString()) + "\n")
    output += "\n"
    output += ("  " + "Edges:\n")
    edges.foreach(edge => output += ("    " + edge.toString()) + "\n")
    output
  }

  def toProtobuf = pb

  // unary argument constructors

  def this(adjacencyList: AdjacencyList) = {
    this(adjacencyList, null, null)

    adjacencyList.Nodes.foreach {node =>
      nodes += ((node.id, node))
    }

    adjacencyList.Edges.foreach {edge =>
      edges += ((edge.id, edge))
    }
  }

  def this(cdfg : UnitCdfgGraph) = {
    this(null, cdfg, null)

    // the following are used to make lookup more efficient
    var unitToId       = scala.collection.mutable.HashMap[soot.Unit, Long]()
    var localToId      = scala.collection.mutable.HashMap[soot.Local, Long]()
    var edgePairToId   = scala.collection.mutable.HashMap[(Long, Long), Long]()
    var idToMethodStrs = scala.collection.mutable.HashMap[Long, Array[String]]()

    val defEdges = cdfg.defEdges()
    val useEdges = cdfg.useEdges()

    def addDataNode(
                     local : soot.Local,
                     name : String,
                     datatype : String
                   ) = {
      val id = getNewId
      val node = new DataNode(id, name, datatype)
      localToId += ((local,id))
      addNode(id, node)
    }

    def addMethodNode(
                       unit : soot.Unit,
                       assignee : Option[String],
                       invokee : Option[Long],
                       name : String,
                       argumentStrings : Array[String]
                     ) : (Long, Node) = {
      val id   = getNewId
      val node = new MethodNode(
        id,
        invokee,
        name,
        Vector.fill(argumentStrings.length)(0) : Vector[Long]
      )
      addNode(id, node)
      unitToId += ((unit, id))
      idToMethodStrs += ((id, argumentStrings))
      (id, node)
    }

    def addMiscNode(
                     unit : soot.Unit
                   ) : (Long, Node) = {
      val id = getNewId
      val node = new MiscNode(id)
      addNode(id, node)
      unitToId += ((unit, id))
      (id, node)
    }

    // CDFG
    def addDefEdge(fromId : Long, toId : Long): Unit = {
      val id = getNewId
      val edge = new DefEdge(id, fromId, toId)
      edges += ((id, edge))
      edgePairToId += (((fromId, toId), id))
    }

    // CDFG
    def addUseEdge(fromId : Long, toId : Long): Unit = {
      val id = getNewId
      val edge = new UseEdge(id, fromId, toId)
      edges += ((id, edge))
      edgePairToId += (((fromId, toId), id))
    }


    // CDFG
    def addControlEdge(fromId : Long, toId : Long): Unit = {
      val id = getNewId
      val edge = new ControlEdge(id, fromId, toId)
      edges += ((id, edge))
      edgePairToId += (((fromId, toId), id))
    }


    def addDefEdges(unit : soot.Unit, unitId : Long): Unit = {
      if (!defEdges.containsKey(unit)) {
        return
        // defensive programming; don't know if defEdges has a value for every unit
      }
      val localIds : Array[Long] = defEdges.get(unit).iterator().map({local : soot.Local =>
        localToId(local)
      }).toArray
      localIds.foreach({localId : Long => addDefEdge(unitId, localId)
      })
    }

    def addUseEdges(local : soot.Local, localId : Long): Unit = {
      if (!useEdges.containsKey(local)) {
        return
        // defensive programming; don't know if useEdges has a value for every local
      }
      val unitIds : Array[Long] = useEdges.get(local).iterator().map({unit : soot.Unit =>
        unitToId(unit)
      }).toArray
      unitIds.foreach({unitId : Long => addUseEdge(localId, unitId)
      })
    }

    def addControlEdges(unit : soot.Unit, unitId : Long): Unit = {
      // add predecessor edges, if not extant
      val unitId = unitToId(unit)
      cdfg.getPredsOf(unit).iterator().foreach{ (predUnit) =>
        val predUnitId = unitToId(predUnit)
        if (!edgePairToId.contains(predUnitId, unitId)) {
          addControlEdge(predUnitId, unitId)
        }
      }
      // add succesor edges, if not extant
      cdfg.getSuccsOf(unit).iterator().foreach{ (succUnit) =>
        val succUnitId = unitToId(succUnit)
        if (!edgePairToId.contains(unitId, succUnitId)) {
          addControlEdge(unitId, succUnitId)
        }
      }
    }

    logger.debug("### Adding local/data nodes...")
    cdfg.localsIter().foreach {
      case n =>
        logger.debug("Found local/data node " + n.getName + " : " + n.getType.toString)
        logger.debug("  node type of " + n.getClass.toString)
        n match {
          case n : JimpleLocal =>
            addDataNode(n, n.getName, n.getType.toString)
            logger.debug("    Node added!")
          case m =>
            logger.debug("    Data node of unknown type; ignoring...")
        }
    }

    logger.debug("### Adding unit/command nodes & def-edges ...")
    cdfg.unitIterator.foreach {
      case n =>
        logger.debug("Found unit/command node of type " + n.getClass.toString)
        n match {
          case n : IdentityStmt =>
            // must have NO arguments to toString(), which MUST have parens;
            // otherwise needs a pointer to some printer object
            logger.debug("    Data node of unknown type; adding misc node...")
            val (id, _) = addMiscNode(n)
            logger.debug("    Node added!")
            addDefEdges(n, id)
            logger.debug("    Def-edge added!")
          case n : InvokeStmt =>
            val declaringClass = n.getInvokeExpr.getMethod.getDeclaringClass.getName
            val methodName = n.getInvokeExpr.getMethod.getName
            // must have empty arguments to toString(); otherwise needs a pointer to some printer object
            val arguments = n.getInvokeExpr.getArgs
            logger.debug("    declaringClass = " + declaringClass)
            logger.debug("    methodName = " + methodName)
            logger.debug("    Arguments:")
            var i : Int = 1
            arguments.iterator.foreach({argument =>
              logger.debug("      " + i.toString + " = " + argument.toString())
              i += 1
            })
            logger.debug("    " + n.toString())
            val argumentStrings = arguments.iterator.foldRight(new ArrayBuffer[String]())(
              (argument, array) => array += argument.toString
            )
            val (id, _) = addMethodNode(n, None, None, declaringClass + "." + methodName, argumentStrings.toArray)
            logger.debug("    Node added!")
            addDefEdges(n, id)
            logger.debug("    Def-edge added!")
          case n : AssignStmt =>
            val assignee  = n.getLeftOp.toString()
            if (n.containsInvokeExpr()) {
              val declaringClass = n.getInvokeExpr.getMethod.getDeclaringClass.getName
              val methodName = n.getInvokeExpr.getMethod.getName
              // must have empty arguments to toString(); otherwise needs a pointer to some printer object
              val arguments = n.getInvokeExpr.getArgs
              logger.debug("    Assignee       = " + assignee)
              logger.debug("    declaringClass = " + declaringClass)
              logger.debug("    methodName     = " + methodName)
              logger.debug("    Arguments:")
              var i : Int = 1
              arguments.iterator.foreach({argument =>
                logger.debug("      " + i.toString + " = " + argument.toString())
                i += 1
              })
              logger.debug("    " + n.toString())
              val argumentStrings = arguments.iterator.foldRight(new ArrayBuffer[String]())(
                (argument, array) => array += argument.toString()
              )
              val (id, _) = addMethodNode(
                n,
                Some(assignee),
                None,
                declaringClass + "." + methodName,
                argumentStrings.toArray
              )
              logger.debug("    Node added!")
              addDefEdges(n, id)
              logger.debug("    Def-edge added!")
            } else {
              logger.debug("    Data node doesn't use invocation; adding empty misc node...")
              val (id, _) = addMiscNode(n)
              logger.debug("    Node added!")
              addDefEdges(n, id)
              logger.debug("    Def-edge added!")
            }
          case _ =>
            logger.debug("    Data node of unknown type; adding misc node...")
            val (id, _) = addMiscNode(n)
            logger.debug("    Node added!")
            addDefEdges(n, id)
            logger.debug("    Def-edge added!")
        }
    }

    logger.debug("### Adding use-edges...")
    cdfg.localsIter().foreach {
      case n =>
        logger.debug("Found local/data node " + n.getName + " : " + n.getType.toString)
        logger.debug("  node type of " + n.getClass.toString)
        n match {
          case n : JimpleLocal =>
            addUseEdges(n, localToId(n))
            logger.debug("    Use-edge(s) added!")
          case m =>
            logger.debug("    Data node of unknown type; ignoring...")
        }
    }
    logger.debug("### Adding control-edges...")
    cdfg.unitIterator.foreach { n =>
      logger.debug("Found unit/command node of type " + n.getClass.toString)
      addControlEdges(n, unitToId(n))
      logger.debug("Unadded control-edges added.")
    }
    logger.debug("### Removing unconnected nodes...")
    nodes.foreach({ case (id, _) =>
      val connection = edges.values.find(edge => edge.from == id || edge.to == id)
      if (connection.isEmpty) {
        removeNode(id)
      }
    })

    logger.debug("### Extending method nodes w/ arg. ids from arg. names & use-edges...")
    nodes
      .filter(_._2.isInstanceOf[MethodNode])
      .foreach { case (id, methodNode : MethodNode) =>
        val argumentNames = idToMethodStrs(id)
        logger.debug("Found method node " + methodNode.toString)
        logger.debug("  Arguments: ")
        argumentNames.foreach{ argumentName =>
          logger.debug("  " + argumentName)
        }
        argumentNames.zipWithIndex.foreach { case (argumentName, index) =>
          logger.debug("  Finding id of argument " + index + " with name " + argumentName + "...")
          val argumentPairs = nodes
            .filter(_._2.isInstanceOf[DataNode])
            .filter(_._2.asInstanceOf[DataNode].name == argumentName)
          if (argumentPairs.nonEmpty) {
            logger.debug("    Data node pair for argument is " + argumentPairs.head.toString())
            val argVal : Long = argumentPairs.head._1
            nodes.remove(methodNode.id)
            nodes += ((methodNode.id, MethodNode(
              methodNode.id,
              methodNode.invokee,
              methodNode.name,
              methodNode.argumentIds.updated(index, argVal)
            )))
          } else {
            logger.debug("    Argument is a hard string. Ignoring...")
            val argVal : Long = 0
            nodes.remove(methodNode.id)
            nodes += ((methodNode.id, MethodNode(
              methodNode.id,
              methodNode.invokee,
              methodNode.name,
              methodNode.argumentIds.updated(index, argVal)
            )))
          }
        }
      }

    logger.debug("### Adding argument ids from argument names and use-edges...")

    edges
      .filter(_._2.isInstanceOf[UseEdge])
      .foreach { case (_, edge : UseEdge) => nodes.get(edge.to).get match {
        case node : MethodNode =>
          if (! node.argumentIds.toStream.exists(_.equals(edge.from))) {
            val invokee = Some(edge.from)
            nodes.remove(node.id)
            nodes += ((node.id,
              MethodNode(node.id, invokee, node.name, node.argumentIds)
            ))
          }
        case _ => Nil
      }}
  }

  def this(protobuf : ProtoAcdfg.Acdfg) = {
    this(null, null, protobuf)

    // for Protobuf
    def addDataNode(
                     id : Long,
                     name : String,
                     datatype : String
                   ) = {
      val node = new DataNode(id, name, datatype)
      addNode(id, node)
    }

    // Protobuf
    def addMethodNode(
                       id : Long,
                       invokee : Option[Long],
                       name : String,
                       arguments : Vector[Long]
                     ): (Long, Node) = {
      val node = new MethodNode(
        id,
        invokee,
        name,
        arguments
      )
      addNode(id, node)
      (id, node)
    }

    // Protobuf
    def addMiscNode(
                     id : Long
                   ) : (Long, Node) = {
      val node = new MiscNode(id)
      addNode(id, node)
      (id, node)
    }

    // Protobuf
    def addDefEdge(id : Long, from : Long, to : Long): Unit = {
      val edge = new DefEdge(id, from, to)
      edges += ((id, edge))
    }

    // Protobuf
    def addUseEdge(id : Long, from : Long, to : Long): Unit = {
      val edge = new UseEdge(id, from, to)
      edges += ((id, edge))
    }

    // Protobuf
    def addControlEdge(id : Long, from : Long, to : Long): Unit = {
      val edge = new ControlEdge(id, from, to)
      edges += ((id, edge))
    }
    // add data nodes
    protobuf.getDataNodeList.foreach { dataNode =>
      addDataNode(dataNode.getId, dataNode.getName, dataNode.getType)
    }
    protobuf.getMethodNodeList.foreach { methodNode =>
      if (methodNode.hasInvokee) {
        addMethodNode(
          methodNode.getId: Long,
          Some(methodNode.getInvokee) : Option[Long],
          methodNode.getName : String,
          methodNode.getArgumentList.asScala.toVector.map(_.longValue())
        )
      } else {
        addMethodNode(
          methodNode.getId: Long,
          None : Option[Long],
          methodNode.getName : String,
          methodNode.getArgumentList.asScala.toVector.map(_.longValue())
        )
      }
    }
    protobuf.getMiscNodeList.foreach { miscNode =>
      addMiscNode(miscNode.getId)
    }

    protobuf.getControlEdgeList.foreach { ctrlEdge =>
      addControlEdge(ctrlEdge.getId, ctrlEdge.getFrom, ctrlEdge.getTo)
    }

    protobuf.getUseEdgeList.foreach { useEdge =>
      addUseEdge(useEdge.getId, useEdge.getFrom, useEdge.getTo)
    }

    protobuf.getDefEdgeList.foreach { defEdge =>
      addDefEdge(defEdge.getId, defEdge.getFrom, defEdge.getTo)
    }
  }
}