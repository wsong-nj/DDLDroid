package tool.basicAnalysis;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewModelAnalyzer {
    private List<SootClass> viewModels;
    /**viewmodel类及其对应的fields*/
    private Map<SootClass,Chain<SootField>> fieldsMap;
    private List<SootMethod> sources;
    private List<SootMethod> sinks;
    private List<SootMethod> dirtySources;
    private List<SootMethod> dirtySinks;

    public ViewModelAnalyzer() {
    }

    public ViewModelAnalyzer(List<SootClass> viewModels) {
        this.viewModels = viewModels;
        fieldsMap = new HashMap<>();
        sources = new ArrayList<>();
        sinks = new ArrayList<>();
        dirtySources = new ArrayList<>();
        dirtySinks = new ArrayList<>();
    }

    /** 分析viewmodel，生成关于viewmodel的source&sink定义 */
    public void fun() throws IOException {
        this.viewModels = AppParser.v().getViewmodels();
        for (SootClass vmClass : viewModels){
            fieldsMap.put(vmClass,vmClass.getFields());
            getSourceMethod(vmClass);
            getSinkMethod(vmClass);

            // todo 在构造方法中分析是否有在viewmodel对象新建时恢复field，基于SourcesAndSinks.txt中的source方法的调用. field = restoreSource()
            // 若一个field没有通过这些从disk恢复，则所有返回这个field的source均为dirty，导致pd下的loss
            boolean isRecoveredFromDisk = false;
            List<SootMethod> constructors = getConstructor(vmClass);
            for (SootMethod constructor : constructors){
                // dirtySource.add()
                dirtySources.addAll(sources);
            }

            // todo 在普通方法中分析是否有在保存field到disk，基于SourcesAndSinks.txt中的sink方法的调用。saveSink(field)
            // 若一个field没有通过这些往disk保存，则所有更新保存这个field的sink均为dirty，无法度过进程死亡导致pd下的loss
            boolean isSavedToDisk = false;
            // dirtySinks.add();
            dirtySinks.addAll(sinks);
        }
        // 将ViewModel分析结果存在一个临时生成的txt文件中，并在分析结束时删除
        generateTXT();
    }

    /** 把收集到的source sink方法写到临时txt文本中 */
    private void generateTXT() throws IOException {
        File tempTXT = new File("./temp.txt");
        if (tempTXT.exists())
            tempTXT.delete();
        FileOutputStream fileOutputStream = new FileOutputStream(tempTXT);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
        BufferedWriter bw = new BufferedWriter(outputStreamWriter);
        for (SootMethod sootMethod:sources){
            bw.write(sootMethod.getSignature()+" -> _SOURCE_");
            bw.newLine();
        }
        for (SootMethod sootMethod:sinks){
            bw.write(sootMethod.getSignature()+" -> _SINK_");
            bw.newLine();
        }

        bw.close();
        outputStreamWriter.close();
        fileOutputStream.close();
        System.out.println();
    }

    /**解析形参viewmodel类的所有sink方法*/
    private void getSinkMethod(SootClass viewModel) {
        // 方法A，
        for (SootMethod sootMethod : viewModel.getMethods()){
            Body body = sootMethod.retrieveActiveBody();
            UnitGraph unitGraph = new ExceptionalUnitGraph(body);
            MHGPostDominatorsFinder<Unit> pdfinder = new MHGPostDominatorsFinder<>(unitGraph);
            // 完成数据更新一定需要传参数进来嘛?
            // 因为不传参数就认为不是sink不太合理。
            // 可以直接更新viewmodel里的数据，然后使用新的值更新界面，这样就不需要后续数据流进保存操作了。暂不处理，漏掉一些数据流，并不多
            // 判断这种情况需要自己处理对象敏感性，过于复杂
            // 目前处理，数据线更新 -- flow --> setter保存到viewmodel
            if (sootMethod.getParameterCount()==0 || sootMethod.isConstructor() || !sootMethod.isConcrete())
                continue;
            // 过近似，所有可接受参数的方法都定义为sink。影响不大，一般不存在数据流从数据更改source流到无关紧要的sink方法
            // 就算有，也只是错误地认为该数据存在对应的数据流，从而可以被viewmodel保存，从而导致认为没有loss bug，导致一些漏报而已，很少很少几乎没有
            sinks.add(sootMethod);

        }
    }

    /**解析形参viewmodel类的所有source方法。即通过getter方法从viewmodel获得数据的值，作为数据恢复时的source*/
    private void getSourceMethod(SootClass viewModel) {
        for (SootMethod sootMethod : viewModel.getMethods()){
            Body body = sootMethod.retrieveActiveBody();
            UnitGraph unitGraph = new ExceptionalUnitGraph(body);
            MHGDominatorsFinder<Unit> dfinder = new MHGDominatorsFinder<>(unitGraph);
            for (Unit unit : unitGraph.getTails()){
                Stmt stmt = (Stmt) unit;
                if(stmt instanceof ReturnStmt && !(stmt instanceof ReturnVoidStmt)){
                    Value returnvalue = ((ReturnStmt) stmt).getOp();
                    for (Unit preunit : dfinder.getDominators(stmt)){
                        Stmt prestmt = (Stmt) preunit;
                        if (prestmt instanceof DefinitionStmt){
                            Value left = ((DefinitionStmt) prestmt).getLeftOp();
                            if (left.equivTo(returnvalue)){ // 返回值变量在此被赋值
                                Value right = ((DefinitionStmt) prestmt).getRightOp();
                                if (prestmt.containsFieldRef()){
                                    SootField field = prestmt.getFieldRef().getField();
                                    if (right.toString().contains(field.toString())){
                                        sources.add(sootMethod);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 得到形参viewmodel类的构造函数列表 */
    private List<SootMethod> getConstructor(SootClass vm) {
        List<SootMethod> inits = new ArrayList<>();
        for (SootMethod sootMethod : vm.getMethods()){
            if (sootMethod.isConstructor()){
                inits.add(sootMethod);
            }
        }
        return inits;
    }

    /** viewmodel相关的source sink中，是否包含 */
    public boolean containsSourceSink(String methodSig, boolean isSource, boolean isSink) {
        if (isSource){
            for (SootMethod sm : sources){
                if (sm.getSignature().equals(methodSig))
                    return true;
            }
        }
        else if (isSink){
            for (SootMethod sm : sinks){
                if (sm.getSignature().equals(methodSig))
                    return true;
            }
        }

        return false;
    }

    public List<SootMethod> getDirtySources() {
        return dirtySources;
    }

    public void setDirtySources(List<SootMethod> dirtySources) {
        this.dirtySources = dirtySources;
    }

    public List<SootMethod> getDirtySinks() {
        return dirtySinks;
    }

    public void setDirtySinks(List<SootMethod> dirtySinks) {
        this.dirtySinks = dirtySinks;
    }

    public List<SootMethod> getSources() {
        return sources;
    }

    public void setSources(List<SootMethod> sources) {
        this.sources = sources;
    }

    public List<SootMethod> getSinks() {
        return sinks;
    }

    public void setSinks(List<SootMethod> sinks) {
        this.sinks = sinks;
    }
}
