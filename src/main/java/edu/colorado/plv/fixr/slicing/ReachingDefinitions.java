package edu.colorado.plv.fixr.slicing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AnyNewExpr;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.AbstractBoundedFlowSet;
import soot.toolkits.scalar.ArrayFlowUniverse;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import soot.toolkits.scalar.ForwardFlowAnalysis;

/**
 * Implements a reaching definition analysis.
 *
 * @author Sergio Mover
 *
 */
public class ReachingDefinitions extends ForwardFlowAnalysis<Unit, FlowSet> {
  private Map<Unit, Set<Local>> unit2LocalsMap;
  private Map<Unit, Set<Local>> underUnit2LocalsMap;
  private Map<Unit, FlowSet> kill;
  private Map<Unit, FlowSet> gen;
  private AbstractBoundedFlowSet baseSet;

  public ReachingDefinitions(DirectedGraph<Unit> graph) {
    super(graph);

    /* kill and gen are computed correctly for unit graphs and not block graphs */
    assert graph instanceof UnitGraph;

    unit2LocalsMap = new HashMap<Unit, Set<Local>>();
    underUnit2LocalsMap = new HashMap<Unit, Set<Local>>();

    /* set */
    kill = new HashMap<Unit,FlowSet>();
    gen = new HashMap<Unit,FlowSet>();

    /* Collects all the unit of the graph, creating a new domain.
     *
     * For each unit, build the kill and gen set.
     *
     * KILL(u) := {all the other units that define the same element of u}
     * GEN(u) := {all the definitions generated by u}
     *
     */
    Set<Unit> defUnit = new HashSet<Unit>();
    for (Unit u : graph) {
      if (u.getDefBoxes().size() > 0) {
        defUnit.add(u);
      }
    }

    FlowUniverse<Unit> defsUniverse;
    defsUniverse = new ArrayFlowUniverse<Unit>(defUnit.toArray(new Unit[defUnit.size()]));
    baseSet = new ArrayPackedSet(defsUniverse);

    for (Unit u : defUnit) {
      computeSets(u);
    }

    this.doAnalysis();
  }

  @Override
  protected void flowThrough(FlowSet in, Unit unit, FlowSet out) {
    /* The out definition for unit is:
     * out := (in \ kill(unit)) U gen(unit)
     */
    FlowSet killSet = getKill(unit);
    FlowSet genSet = getGen(unit);

    out.clear();
    in.copy(out); /* copies in into out */
    if (killSet != null) {
      out.difference(killSet); /* remove killSet from out */
    }
    if (genSet != null) {
      out.union(genSet); /* union out with genSet, putting the result in out */
    }
  }

  @Override
  protected void copy(FlowSet source, FlowSet dest) {
    source.copy(dest);
  }

  @Override
  protected FlowSet entryInitialFlow() {
    /* We start with an empty set, adding variables on the way */
    return baseSet.emptySet();
  }

  @Override
  protected void merge(FlowSet in1, FlowSet in2, FlowSet out) {
    /* we merge the two in elements with the out element */
    out.union(in1);
    out.union(in2);
  }

  @Override
  protected FlowSet newInitialFlow() {
    /* We start with an empty set, adding variables on the way */
    return baseSet.emptySet();
  }

  protected FlowSet getKill(Unit u) {
    return kill.get(u);
  }

  protected FlowSet getGen(Unit u) {
    return gen.get(u);
  }

  protected void computeSets(Unit u) {
    FlowSet killSet = baseSet.emptySet();
    FlowSet genSet = baseSet.emptySet();

    /* get the set of units that declare */
    for (Local l : getDefLocals(u, false)) {
      for (Iterator<Unit> unitIter = baseSet.topSet().iterator();
           unitIter.hasNext();) {
        Unit u2 = unitIter.next();
        if (u2 != u && getDefLocals(u2, false).contains(l)) {
          killSet.add(u2);
        }
      }
    }
    genSet.add(u);

    kill.put(u, killSet);
    gen.put(u, genSet);
  }

  /**
   * Get the set of locals defined by unit.
   *
   * @param unit
   * @param overapprox
   * @return
   */
  public Set<Local> getDefLocals(Unit unit, boolean overapprox) {
    if (overapprox) {
      return getOverApproxDefLocals(unit);
    }
    else {
      return getUnderApproxDefLocals(unit);
    }
  }

  protected Set<Local> getUnderApproxDefLocals(Unit unit) {
    // TODO Extend to distinguish field and array accesses
    // TODO Extend to handle method calls

    Set<Local> locals = underUnit2LocalsMap.get(unit);
    if (null != locals) return locals;

    locals = new HashSet<Local>();

    boolean newExpr = false;
    if (unit instanceof AssignStmt) {
      Value rhs = ((AssignStmt) unit).getRightOp();
      newExpr = rhs instanceof AnyNewExpr;
    }

    for (ValueBox vb : unit.getDefBoxes()) {
      Value v = vb.getValue();
      if (v instanceof Local) {
        locals.add((Local) v);
      }
      else if (v instanceof ArrayRef) {
        Value arrayBase = ((ArrayRef) v).getBase();
        assert arrayBase instanceof Local;
        if (newExpr) locals.add((Local) arrayBase);
      }
      else if (v instanceof InstanceFieldRef) {
        Value instanceBase = ((InstanceFieldRef) v).getBase();
        assert instanceBase instanceof Local;
        if (newExpr) locals.add((Local) instanceBase);
      }
    }

    underUnit2LocalsMap.put(unit, locals);
    return locals;
  }

  protected Set<Local> getOverApproxDefLocals(Unit unit) {
    // TODO Extend to distinguish field and array accesses
    // TODO Extend to handle method calls

    Set<Local> locals = unit2LocalsMap.get(unit);
    if (null != locals) return locals;

    locals = new HashSet<Local>();

    for (ValueBox vb : unit.getDefBoxes()) {
      Value v = vb.getValue();
      if (v instanceof Local) {
        locals.add((Local) v);
      }
      else if (v instanceof ArrayRef) {
        Value arrayBase = ((ArrayRef) v).getBase();
        assert arrayBase instanceof Local;
        locals.add((Local) arrayBase);
      }
      else if (v instanceof InstanceFieldRef) {
        Value instanceBase = ((InstanceFieldRef) v).getBase();
        assert instanceBase instanceof Local;
        locals.add((Local) instanceBase);
      }
    }

    unit2LocalsMap.put(unit, locals);
    return locals;
  }

  /**
   * True if locals (or values more general) used in srcUnit are defined in
   * dstUnit
   *
   * @param srcUnit
   * @param dstUnit
   * @return
   */
  public boolean unitDefines(Unit srcUnit, Unit dstUnit, boolean overapprox) {
    Set<Local> defsInDst = this.getDefLocals(dstUnit, overapprox);

    for (soot.ValueBox vb : srcUnit.getUseBoxes()) {
      Value v = vb.getValue();
      if (v instanceof Local) {
        if (defsInDst.contains(v)) return true;
      }
      else if (v instanceof ArrayRef) {
        Value arrayBase = ((ArrayRef) v).getBase();
        assert arrayBase instanceof Local;
        if (defsInDst.contains(arrayBase)) return true;
      }
      else if (v instanceof InstanceFieldRef) {
        Value instanceBase = ((InstanceFieldRef) v).getBase();
        assert instanceBase instanceof Local;
        if (defsInDst.contains(instanceBase)) return true;
      }
    }

    return false;
  }

  /**
   * Get the reachable definition at unit u.
   *
   * @param u
   * @return
   */
  public List<Unit> getReachableAt(Unit u) {
    /* get the reachable definition at unit u */
    ArrayPackedSet s = (ArrayPackedSet) getFlowAfter(u);
    @SuppressWarnings("unchecked")
    List<Unit> unitList = (List<Unit>) s.toList();

    return unitList;
  }
  
  /**
   * Returns a map that, given a Unit u, returns the set of all the units that 
   * can be reached by the definitions in u and use u (a DU chain)
   *  
   * @param u
   * @return 
   */
  public Map<Unit, Set<Unit>> getDefinedUnits() {
     Map<Unit, Set<Unit>> duMap = new HashMap<Unit, Set<Unit>>();
     
     for (Unit dst : graph) {
       for (Object objSrc : getFlowAfter(dst) ) {
         Unit src = (Unit) objSrc;
         
         if (unitDefines(dst, src, true)) {
           Set<Unit> srcSet = duMap.get(src);
           if (null == srcSet) {
             srcSet = new HashSet<Unit>();
             duMap.put(src, srcSet);           
           }
           
           srcSet.add(dst);
         }
       }         
     }
     
     return duMap;
  }
}
