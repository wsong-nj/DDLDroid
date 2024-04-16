package tool.analysisForTwoCase;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IfStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import soot.toolkits.graph.UnitGraph;

import java.util.List;

public class ControlDependency {
    // sootMethod()方法内的ifStmt,变量是conditionLocal
    private IfStmt ifStmt;
    private Value conditionLocal;
    private SootMethod sootMethod;

    public ControlDependency() {
    }

    public ControlDependency(IfStmt ifStmt, Value conditionLocal, SootMethod sootMethod) {
        this.ifStmt = ifStmt;
        this.conditionLocal = conditionLocal;
        this.sootMethod = sootMethod;
    }

    private MHGDominatorsFinder<Unit> getDfinder(){
        Body body = sootMethod.retrieveActiveBody();
        UnitGraph unitGraph = new ExceptionalUnitGraph(body);
        MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱
        return dFinder;
    }

    public List<Unit> getPres(){
        MHGDominatorsFinder<Unit>  dFinder = getDfinder();
        List<Unit> pres = dFinder.getDominators(ifStmt);
        return pres;
    }

    public IfStmt getIfStmt() {
        return ifStmt;
    }

    public void setIfStmt(IfStmt ifStmt) {
        this.ifStmt = ifStmt;
    }

    public Value getConditionLocal() {
        return conditionLocal;
    }

    public void setConditionLocal(Value conditionLocal) {
        this.conditionLocal = conditionLocal;
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public void setSootMethod(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
    }
}
