package tool.analysisForTwoCase;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIfStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import tool.basicAnalysis.AppParser;
import tool.utils.Logger;

import java.io.IOException;
import java.util.*;

public class AnalysisStart {
    // sth fields definitions here;
    private static List<SootClass> classes;
    static CallGraph callGraph;
    private String appName;
    private String apkDir;

    public AnalysisStart(String apkDir, String appName, CallGraph callGraph) {
        this.apkDir = apkDir;
        this.appName = appName;
        this.callGraph = callGraph;

    }

    public void analyzeAPk(long startForOneApp) throws IOException {
        System.out.println("Now analyzing this app: "+appName);

        classes = AppParser.v().getAllClasses();
        System.out.println("classes.size(): "+classes.size());

        System.out.println("\n---------- Analysis Start  ----------");
        //TestCg(callGraph);
        //TestIfStmt(classes);
        //Test02();

        System.out.println("\n---------- Data loss Detecting ----------");
        String resultDir = apkDir+appName;
        AnalysisForDL anaCC = new AnalysisForDL(AppParser.v().getActivities(), AppParser.v().getFragments(), callGraph, resultDir);
        anaCC.detectActivity(startForOneApp);


    }

    private void TestCg(CallGraph callGraph) {

        SootMethod sootMethod = null;
        sootMethod = Scene.v().getMethod("<com.example.viewmedeltest.MainActivity$1: void onClick(android.view.View)>");
        if (sootMethod !=null){
            Body body = sootMethod.retrieveActiveBody();
            MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(new ExceptionalUnitGraph(body)); // 支配者，其必经过的前驱
            Iterator<Edge> outOf = callGraph.edgesOutOf(sootMethod);
            List<SootMethod> callees = new ArrayList<>();
            Stmt stmt1 = null;
            while(outOf.hasNext()){
                Edge edge = outOf.next();
                if (edge.tgt().getName().equals("getNumber")){
                    System.out.println(edge);
                    Stmt stmt = edge.srcStmt();
                    System.out.println(stmt);
                    System.out.println(dFinder.getDominators(stmt).size());
                    if (stmt1==null){
                        stmt1 = stmt;
                    }else{
                        System.out.println(stmt1.equals(stmt));
                    }
                }

                SootMethod sm = edge.getTgt().method();
                if (!sm.isConstructor() && AppParser.v().isAppClass(sm.getDeclaringClass().getName())){ // 构造函数考虑在内将导致栈溢出，因为会涉及到很多非app本身而是java库里的构造函数
                    callees.add(sm);
                }
            }
            System.out.println(callees);
        }else {
            System.out.println("error");
        }

        return;

    }

    private List<SootClass> resolveAllClasses(Chain<SootClass> chain) { // Preliminary filter out classes
        List<SootClass> allClasses = new ArrayList<SootClass>();
        //System.out.println("chain.size():"+chain.size());
        for (SootClass s : chain) {
            if (s.isConcrete()) {
                if (!s.getName().startsWith("android") && !s.getName().startsWith("java")) {
                    allClasses.add(s);
                }
            }
        }
        return allClasses;
    }

    public void Test01(){

        Map<Integer,Integer> h = new HashMap<>();

        Logger.i("AnalysisStart"," run Test():");
        List<SootClass> test = new ArrayList<>();
        SootClass classForTest1 = Scene.v().getSootClassUnsafe("me.tsukanov.counter.e.f"); // me.tsukanov.counter.e.f public boolean w0(android.view.MenuItem)
        SootClass classForTest2 = Scene.v().getSootClassUnsafe("me.tsukanov.counter.activities.MainActivity"); // public boolean onOptionsItemSelected(android.view.MenuItem)
        test.add(classForTest1);
        test.add(classForTest2);
        for (SootClass sc : test){
            System.out.println("\n\n\n");
            System.out.println(sc);

            SootMethod methodForTest;
            Body body = null;

            methodForTest = sc.getMethodByNameUnsafe("w0");
            if (methodForTest!=null){
                System.out.println(methodForTest);
                body = methodForTest.retrieveActiveBody();
            }
            else {
                methodForTest = sc.getMethodByNameUnsafe("onOptionsItemSelected");
                if (methodForTest!=null){
                    System.out.println(methodForTest);
                    body = methodForTest.retrieveActiveBody();
                }
            }
            //System.out.println(body);
            //methodForTest.getPara
            System.out.println(body.getParameterLocals());
            Value value = null;


            if (body!=null){
                UnitGraph unitGraph = new ExceptionalUnitGraph(body);
                MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph); // 反向支配者
                MHGDominatorsFinder<Unit> dFinder = new MHGDominatorsFinder<Unit>(unitGraph); // 支配者
                //System.out.println(body.getUnits().getFirst());
                ValueBox condition = null;
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
//                    if (stmt instanceof IdentityStmt){
//                        System.out.println(stmt);
//                    }

//                    if (stmt instanceof AssignStmt){
//                        if ( ((AssignStmt) stmt).getLeftOp().getType() instanceof IntType ){
//                            if (((AssignStmt) stmt).getRightOp() instanceof InvokeExpr){
//                                System.out.println("\n"+stmt);
//                                System.out.println(((InvokeExpr) ((AssignStmt) stmt).getRightOp()).getMethod().getSignature().equals("<android.view.MenuItem: int getItemId()>"));
//
//                                for(ValueBox vb:((AssignStmt) stmt).getRightOp().getUseBoxes()){
//                                    if (vb.getValue().equals(body.getParameterLocal(0))){
//                                        condition = ((AssignStmt) stmt).getLeftOpBox();
//                                        System.out.println("condition是  "+condition.getValue());
//                                        break;
//                                    }
//                                }
//
//                            }
//                        }
//
//                    }

//                    if (stmt instanceof SwitchStmt){
//                        //SwitchFinder switchFinder = new SwitchFinder();
//                        System.out.println(stmt);
//                        SwitchStmt ss = (SwitchStmt) stmt;
//                        System.out.println(ss.getKey());
//                        System.out.println(ss.getTargets());
//                    }

                    if (stmt instanceof IfStmt){
                        IfStmt ifStmt = (IfStmt) stmt;
                        System.out.println("\n"+ifStmt);
                        System.out.println(ifStmt.getUseBoxes());
                        System.out.println(ifStmt.getCondition());
                        System.out.println(condition);
                        for (ValueBox vb : ifStmt.getCondition().getUseBoxes()){
                            if (vb.getValue().equals(condition.getValue())){
                                System.out.println("zhouyuhao ");
                            }
                        }
                    }
                }

            }
        }
        System.out.println();
        System.out.println();
        System.out.println();

    }

    public void Test02(){
        for (SootClass sootClass : classes){
            for (SootMethod sootMethod : sootClass.getMethods()){
                Body body = sootMethod.retrieveActiveBody();
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof AssignStmt && stmt.containsFieldRef()){
                        SootField sootField = stmt.getFieldRef().getField();
                        boolean flag = ((AssignStmt) stmt).getLeftOp().toString().contains(sootField.toString());

                        if (flag == true){
                            System.out.println(stmt);
                            System.out.println("\t"+((AssignStmt) stmt).getLeftOp());
                            System.out.println("\t"+sootField);
                        }


                    }
                }
            }
        }
    }

    public void TestIfStmt(List<SootClass> classes){

        for (SootClass sootClass: classes){
            for (SootMethod sootMethod : sootClass.getMethods()){
                Body body = sootMethod.retrieveActiveBody();

                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof IfStmt){
                        System.out.println(sootMethod.getSignature());
                        System.out.println(body.getLocals());
                        System.out.println(stmt);
                        JIfStmt stmt1 = (JIfStmt) stmt;
                        Value condition = stmt1.getCondition();

                        for (ValueBox vb : condition.getUseBoxes()){
                            System.out.println(vb.getValue());
                            System.out.println(body.getLocals().contains(vb.getValue()));
                            for (Local local : body.getLocals()){
                                System.out.print(local.equivTo(vb.getValue())+" ");
                            }
                            System.out.println();
                        }
                    }
                }
            }
        }

    }

}
