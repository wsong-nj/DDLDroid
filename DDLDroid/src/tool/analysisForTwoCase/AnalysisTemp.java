package tool.analysisForTwoCase;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

import java.util.*;

public class AnalysisTemp { //case 2 data loss detection

    private static List<SootClass> classesForViewModel;
    private Map<SootField, Boolean> viewDataMap;

    public AnalysisTemp() {
        viewDataMap = new HashMap<>();
    }

    public void detectPDDL(List<SootClass> classes) {
        classesForViewModel = getClassesForViewModel(classes);
        System.out.println("classesForViewmodel size: "+classesForViewModel.size());

        for (SootClass sc : classesForViewModel) {
            System.out.println(">>>类名: "+sc.getName());

            viewDataMap.clear();
            /*1、检索数据，初始化map
            * 2、寻找构造函数
            * 3、遍历函数体body，判断 Defboxes 中的数据是否是map中的数据字段
            * 4、若是，判断赋值语句右半边是否来自savedstatehandle*/

            getAllViewData(sc);

            for (SootMethod sm : sc.getMethods()){
                if (sm.isConstructor()){
                    System.out.println("构造函数"+sm.getSubSignature());
//                    System.out.println(sm.getDeclaringClass().getName());
//                    System.out.println(AppParser.v().getPkg());

                    int i = 0; // index of unit
                    Body body = sm.retrieveActiveBody();

                    Local paraLocalofSSH = null; // SSH means androidx.lifecycle.SavedStateHandle;
                    for (Local local : body.getParameterLocals()){
                        if (local.getType().equals(Scene.v().getType("androidx.lifecycle.SavedStateHandle"))){
                            paraLocalofSSH = local;
                            System.out.println("paraLocalofSSH:"+paraLocalofSSH);
                            break;
                        }
                    }
                    if (paraLocalofSSH == null){
                        //System.out.println("This ViewModel class constructor has No androidx.lifecycle.SavedStateHandle Para!");
                        break;
                    }

                    UnitGraph unitGraph = new BriefUnitGraph(body);
                    MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph); // 基本块内的后继查询
                    MHGDominatorsFinder<Unit> df = new MHGDominatorsFinder(unitGraph); // 基本块内的前驱查询
                    JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();

                    for (Unit unit : body.getUnits()){
                        Stmt stmt = (Stmt) unit;
                        if (stmt instanceof AssignStmt && stmt.containsFieldRef()){ // 赋值语句，且包含field变量
                            Value leftOp = ((AssignStmt) stmt).getLeftOp(); // 被赋值的左操作数
                            Value right = ((AssignStmt) stmt).getRightOp();
                            SootField field = stmt.getFieldRef().getField();
                            Value fieldValue = stmt.getFieldRefBox().getValue();
                            if (leftOp.equals(fieldValue)  && !(right instanceof Constant) ){ // 被赋值的左操作数，即是，被赋值的field变量
                                //System.out.println("包含定义字段:"+fieldValue);
                                viewDataMap.replace(field,false);

                                Value rightOp = ((AssignStmt) stmt).getRightOp();

                                List<Unit> preds = df.getDominators(stmt);

                                boolean flag = false;

                                for (int j = preds.size()-1; j >=0 ; j--) { // 逆序遍历执行语句
                                    // 第一句是stmt自己，直接continue，从size()-2开始
                                    Unit fatherUnit = preds.get(j);
                                    Stmt fatherStmt = (Stmt) fatherUnit;

                                    //System.out.println("当前RIGHTOP是："+rightOp);
                                    //System.out.println(fatherStmt);

                                    if (fatherStmt instanceof IdentityStmt){
                                        Value leftTemp = ((IdentityStmt) fatherStmt).getLeftOp();
                                        Value rightTemp = ((IdentityStmt) fatherStmt).getRightOp();
                                        if (leftTemp.equals(rightOp) && leftTemp.equals(paraLocalofSSH) && rightTemp.toString().contains("androidx.lifecycle.SavedStateHandle")){

                                            System.out.println("周宇豪操作MAP");
                                            flag = true;
                                            break;
                                        }
                                    }

                                    if (fatherStmt instanceof AssignStmt){
                                        Value leftTemp = ((AssignStmt) fatherStmt).getLeftOp();
                                        Value rightTemp = ((AssignStmt) fatherStmt).getRightOp();
                                        if (leftTemp.equals(rightOp)){ // 找到赋值链的上一个节点，准备更新rightOp
                                            // 获取rightTemp中的local变量，更新rightOp

                                            if (rightTemp instanceof InvokeExpr){
//                                            System.out.println("周宇豪rightTemp instanceof InvokeExpr："+getCallerLocalofInvokeExpr((InvokeExpr) rightTemp,bodyLocals));
                                                rightOp = getInvokerValue((InvokeExpr) rightTemp);
                                            }
                                            else if (rightTemp instanceof CastExpr){
                                            System.out.println("周宇豪righttemp是Local"+((CastExpr) rightTemp).getOp());
                                                rightOp = ((CastExpr) rightTemp).getOp();
                                            }else if (rightTemp instanceof Constant){
                                                //System.out.println("周宇豪右操作数是常量："+rightTemp);
                                                rightOp = rightTemp;
                                            }
                                        }

                                    }else if (fatherStmt instanceof IfStmt){
                                        if (rightOp instanceof Constant){
                                            System.out.println("遇见IF语句和常量");
                                            Value condExpr = ((IfStmt) fatherStmt).getCondition();

                                            rightOp = ((ConditionExpr)condExpr).getOp1();;
                                        }

                                    }else if (fatherStmt instanceof InvokeStmt){ // 没影响直接跳过
                                        ;
                                    }
                                }

                                viewDataMap.replace(field,flag);

                            }

                        }
                        i++;
                    }

                }
            }


            System.out.println("viewDataMap:"+viewDataMap);
            Iterator iterator = viewDataMap.entrySet().iterator();
            while(iterator.hasNext()){
                Map.Entry<SootField,Boolean> entry = (Map.Entry<SootField, Boolean>) iterator.next();
                if(entry.getValue()==false){
                    System.out.println("PDDL happens on Data: "+entry.getKey());
                }
            }

        }
    }

    private void fun(Stmt stmt,UnitGraph unitGraph,SootField field,MHGDominatorsFinder<Unit> df,Local paraLocalofSSH,Chain<Local> bodyLocals) { // 如何准确溯源到androidx.lifecycle.SavedStateHandle参数，即检测数据恢复
        Value rightOp = ((AssignStmt) stmt).getRightOp();
        /*
        * 从rightOp中提取出是哪个local变量
        * */

        List<Unit> preds = df.getDominators(stmt);
//        for (Unit unit : preds)
//            System.out.println(unit);

        for (int i = preds.size()-1; i >=0 ; i--) { // 逆序遍历执行语句
            // 第一句是stmt自己，直接continue，从size()-2开始
            Unit fatherUnit = preds.get(i);
            Stmt fatherStmt = (Stmt) fatherUnit;
            System.out.println(fatherStmt);

            if (fatherStmt instanceof IdentityStmt){
                Value leftTemp = ((IdentityStmt) fatherStmt).getLeftOp();
                Value rightTemp = ((IdentityStmt) fatherStmt).getRightOp();
                if (leftTemp.equals(rightOp) && leftTemp.equals(paraLocalofSSH)
                        && rightTemp.toString().contains("androidx.lifecycle.SavedStateHandle")){

                    System.out.println("周宇豪操作MAP");
                    // 操作 map
                    return;
                }
            }

            if (fatherStmt instanceof AssignStmt){
                Value leftTemp = ((AssignStmt) fatherStmt).getLeftOp();
                Value rightTemp = ((AssignStmt) fatherStmt).getRightOp();
                if (leftTemp.equals(rightOp)){ // 找到赋值链的上一个节点，准备更新rightOp
                    // 获取rightTemp中的local变量，更新rightOp
                    if (rightTemp instanceof InvokeExpr){
                        //System.out.println("周宇豪rightTemp instanceof InvokeExpr："+getCallerLocalofExpr((InvokeExpr) rightTemp,bodyLocals));
                    }
                }else {
                    continue;
                }
            }else if (fatherStmt instanceof IfStmt){
                ;
            }else if (fatherStmt instanceof InvokeStmt){
                ;
            }
//            System.out.println(fatherStmt);
        }


//        if (stmt.containsInvokeExpr())
//            System.out.println("InvokeExpr:"+stmt.getInvokeExpr());
//        if (rightOp instanceof Immediate){
//            System.out.println("立即数");
//        }else if (rightOp instanceof InvokeExpr){
//            System.out.println("rightOp instanceof InvokeExpr: "+rightOp);
//        }
//        if (preds.size()!=0){ // 不是函数体第一句话
//            for (Unit unit : preds){
//                //System.out.println(unit);
//            }
//        }

    }

    private Value getInvokerValue(InvokeExpr invokeExpr) { // useBoxes - argBoxes = invoker
        List<ValueBox> useBoxes = invokeExpr.getUseBoxes();
        List<Value> args = invokeExpr.getArgs();

        for (ValueBox vb : useBoxes){
            if (args.contains(vb.getValue())){
                useBoxes.remove(vb);
            }
        }
        Value invoker = null;
        if (useBoxes.size()==1)
            invoker = useBoxes.get(0).getValue();
        else
            System.out.println("error2");
        return invoker;
    }

    private void getAllViewData(SootClass sootClass) {
        for (SootField sootField : sootClass.getFields()){
            viewDataMap.put(sootField, false);

        }
        //System.out.println(viewDataMap);
    }

    private List<SootClass> getClassesForViewModel(List<SootClass> classes) {
        List<SootClass> allViewModelClasses = new ArrayList<SootClass>();
        for (SootClass sc:classes){
            SootClass father = sc.getSuperclassUnsafe();
            while (father!=null){ //sc.getSuperclassUnsafe()"androidx.lifecycle.ViewModel"
                if (father.getName()=="androidx.lifecycle.ViewModel"){
                    allViewModelClasses.add(sc);
                    break;
                }
                father = father.getSuperclassUnsafe();
            }

        }
        return allViewModelClasses;
    }
}
