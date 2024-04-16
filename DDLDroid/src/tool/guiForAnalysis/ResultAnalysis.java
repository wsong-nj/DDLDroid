package tool.guiForAnalysis;

import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.results.DataFlowResult;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.MethodSourceSinkDefinition;
import tool.analysisForTwoCase.DataVariable;
import tool.analysisForTwoCase.Feature;
import tool.basicAnalysis.ViewModelAnalyzer;
import tool.utils.ClassService;

import java.io.*;
import java.util.*;

public class ResultAnalysis {
    /**界面恢复的控件数据集合*/
    private Map<Feature, List<DataVariable>> Rmap;
    /**界面需要保存的控件数据集合*/
    private Map<Feature, List<DataVariable>> Smap;
    /***/
    private InfoflowResults save;
    /***/
    private InfoflowResults restore;

    private Set<DataFlowResult> restoreResults;
    private Set<DataFlowResult> saveResults;

    private long startTime;
    private long endTime;

    private ViewModelAnalyzer vma;


    public ResultAnalysis(Map<Feature, List<DataVariable>> R, Map<Feature, List<DataVariable>> S, InfoflowResults save, InfoflowResults restore, ViewModelAnalyzer vma, Long startTime) {
        this.Rmap = R;
        this.Smap = S;
        this.save = save;
        this.restore = restore;

        this.restoreResults = restore.getResultSet();
        this.saveResults = save.getResultSet();
        this.startTime = startTime;
        this.vma = vma;
    }

    public void analysisOut(String resultOutputDir) throws IOException {
        String resultFileName = "result.csv";
        File resultCSV = new File(resultOutputDir,resultFileName);
        if (resultCSV.exists())
            resultCSV.delete();
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(resultCSV);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            BufferedWriter bw = new BufferedWriter(outputStreamWriter);
            deleteNoLossOnes(bw);
            endTime = System.currentTimeMillis();
            bw.write("Total time: "+((endTime - startTime) / 1000)+"s");
            bw.close();
            outputStreamWriter.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        File tempTXT = new File("./temp.txt");  // 删除ViewModel分析其产生的临时文件，为下次分析干净环境
        if (tempTXT.exists())
            tempTXT.delete();

    }

    private void deleteNoLossOnes(BufferedWriter bw) throws IOException {
        int count = 0;

        List<Feature> matched = new ArrayList<>();

        for (Map.Entry<Feature, List<DataVariable>> mappingR : Rmap.entrySet()){
            Feature keyR = mappingR.getKey();

            if ( Smap.containsKey(keyR) ){ // 有配控件,分析他们的对应变量的数据流是否完备。完备则无bug，不完备则有bug。分析完加入已匹配
                List<DataVariable> RmapVars = Rmap.get(keyR);
                List<DataVariable> SmapVars = Smap.get(keyR);

                matched.add(keyR);

                // 检查该控件对应的R集合变量
                boolean isRVarBugCC = false;
                boolean isRVarBugPD = false;
                for (DataVariable dvR : RmapVars){
                    if (dvR.getSourcesink()!=null){
                        for (ISourceSinkDefinition sinkDef : dvR.getSourcesink()){ // 一般只有一个定义（即一层循环），有2个定义则是关于dialog的需要保存集合中的
                            DataFlowResult inDataflow = isInRestoreDataflow(sinkDef);
                            if (inDataflow==null){ // 找不到使用此变量定义sink点的数据流，认为恢复的不对
                                isRVarBugCC = true;
                                isRVarBugPD = true;
                            }
                            else{   //todo 确实有数据流，进一步判断恢复数据流的source与ViewModel的关系
                                // source的读取数据method，是否是dirtySource
                                isRVarBugPD = (isRestoreFromVM(inDataflow) && isDirtyRestoreOfVM(inDataflow)) || isRVarBugPD;
                            }
                        }
                    }
                    else{ // 对于恢复的变量，无法定位到相应变量（一般是dialog，drawer等需要控制依赖的，定位不到控制依赖），自然没有正确的恢复的数据流，自然恢复的不对
                        isRVarBugCC = true;
                        isRVarBugPD = true;
                    }
                }

                // 检查该控件对应的S集合变量
                boolean isSVarBugCC = false;
                boolean isSVarBugPD = false;
                for (DataVariable dvS : SmapVars){ // 该控件的每个元素，检查数据流
                    if (dvS.getSourcesink()!=null){
                        for (ISourceSinkDefinition sourceDef : dvS.getSourcesink()){    // 一般只有一个定义（即一层循环），有2个定义则是关于dialog的
                            DataFlowResult inDataflow = isInSaveDataflow(sourceDef);
                            if (inDataflow==null){
                                isSVarBugCC = true;
                                isSVarBugPD = true;
                            }
                            else{   //todo 确实有数据流，进一步判断保存数据流的sink与ViewModel的关系
                                // sink的保存数据method，是否是dirtySink
                                isSVarBugPD = (isSaveToVM(inDataflow) && isDirtySaveOfVM(inDataflow)) || isSVarBugPD;
                            }
                        }
                    }
                    else{
                        isSVarBugCC = true;
                        isSVarBugPD = true;
                    }
                }

                if (isRVarBugCC || isSVarBugCC || isRVarBugPD || isSVarBugPD ){
                    // 进入组合的可能是 CC1-PD1  或者  CC0-PD1, 所以isPDDL always = true
                    // 有一个集合中的map中的变量有bug就认为有bug
                    count++;
                    handleIncorrectSaveRestoreWidget(keyR, RmapVars, SmapVars, bw, isRVarBugCC || isSVarBugCC, true, count);
                }

            }

        }


        for (Map.Entry<Feature, List<DataVariable>> mappingR : Rmap.entrySet()){    // 处理R中失配元素
            Feature key = mappingR.getKey();
            if (matched.contains(key))
                continue;

            if (key.getWidgetType().toString().contains("Dialog")){ // 开屏就启动的对话框
                if (key.getWidgetSetting()==null || key.getWidgetSetting().getSettings().size()==0){
                    continue;
                }
                count++;
                handleBugWidgetForRmap(key, mappingR.getValue(), null, bw, count);
            }
            else {
                List<SootField> widget = key.getWidgetField();
                SootField widgetField = widget.get(widget.size()-1);
                if (widgetField.getType().toString().equals("android.widget.TextView")){    // 检查多态  是否TextView强转EditText
                    // 检查多态  是否TextView强转EditText
                    boolean isEditText = false;

                    SootClass widgetWrapperClass = widgetField.getDeclaringClass(); // 控件定义的所属类
                    boolean isActivity = ClassService.isActivity(widgetWrapperClass);
                    boolean isFragment = ClassService.isFragment(widgetWrapperClass);
                    boolean isView = ClassService.isView(widgetWrapperClass);
                    if ( !isActivity && !isFragment && isView){ // 不是定义在UI控制器中的控件，那么是封装的，检查是不是有类型强转的多态使用。是个View，封装的控件，例如ViewGroup的子类layout
                        for (SootMethod sootMethod : widgetWrapperClass.getMethods()){
                            if (sootMethod.isConstructor()){
                                Body body = sootMethod.retrieveActiveBody();
                                for (Unit unit : body.getUnits()){
                                    Stmt stmt = (Stmt) unit;
                                    if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                                        Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                                        SootField sootField = stmt.getFieldRef().getField();
                                        if (leftOp.toString().contains( sootField.toString() ) && sootField.equals(widgetField)){
                                            Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                                            if (rightOp.getType().toString().equals("android.widget.EditText")){
                                                isEditText = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isEditText){    // 多态，本质是失配的EditText。如果在onPause中有getText，则不会失配
                        count++;
                        handleBugWidgetForRmap(key, mappingR.getValue(), null, bw, count);
                    }
                    // 如果只是普通TextView，则与S集合失配可能只是因为动态注册中设置属性，从而被识别为恢复；而实际上不存在change而需要保存，可减少误报
                    // 因为TextView的change，必须要要显示调用setText()的，只会存在S中的TextView失配，R中一般不会。
                }
                else{   // 不是TextView，无须检查多态；例如EditText.setText()
                    count++;
                    handleBugWidgetForRmap(key, mappingR.getValue(), null, bw, count);
                }
            }

        }

        for (Map.Entry<Feature, List<DataVariable>> mappingS : Smap.entrySet()){
            Feature key = mappingS.getKey();
            if (matched.contains(key))
                continue;

            count++;
            handleBugWidgetForSmap(key, null, mappingS.getValue(), bw, count);
        }

        bw.write("Total loss: "+count);
        bw.newLine();

//        for (indexA = A.size()-1; indexA>=0; indexA--){
//            DataVariable eleA = A.get(indexA);
//            for (indexB = B.size()-1; indexB>=0; indexB--){
//
//                DataVariable eleB = B.get(indexB);
//                boolean isSame = DataVariable.isSame(eleA,eleB);
//
//                if (isSame){    // 存在映射关系,则继续判断有没有数据流？
//
//                    if (eleA.getSourcesink()!=null && eleB.getSourcesink()!=null){
//
//                        // 默认值true，表示默认有bug，满足下述的数据流条件则置为false表示没有bug
//                        boolean isCCDL = true;
//                        boolean isPDDL = true;
//
//                        // 检查恢复集合元素的数据流
//                        if (eleA.getSourcesink()!=null && restoreResults!=null){
//                            // eleA是恢复元素，检查restore的sink
//
//                            for (int i = eleA.getSourcesink().size()-1;i>=0;i--){
//                                isCCDL = true;
//                                isPDDL = true; // 新定义，则再次置回true，表示默认有
//
//                                ISourceSinkDefinition sinkDefinition = eleA.getSourcesink().get(i);
//                                for (DataFlowResult restoreResult : restoreResults){
//                                    if ( sinkDefinition.equals( restoreResult.getSink().getDefinition() ) ){
//                                        isCCDL = false;
//
//                                        // TODO 检查数据流来源，如果来自于viewmodel，则需要进一步判断该source对应的field会不会在viewmodel重建对象时恢复
//                                        ISourceSinkDefinition source = restoreResult.getSource().getDefinition();
//                                        if (source instanceof MethodSourceSinkDefinition){
//                                            String sourceMethodSig = ((MethodSourceSinkDefinition) source).getMethod().getSignature();
//                                            boolean isVmssd = vma.containsSourceSink(sourceMethodSig, true, false);
//                                            if (isVmssd){
//                                                // 进一步检查viewmodel是否正确，
//                                                // 如果不正确，则虽然有关于viewmodel中的sourcesink定义的数据流
//                                                // 但是PDDL则会依然存在。
//                                                // 即 isCCDL = fasle; isPDDL = true;
//
//                                                // TODO 假设viewmodel实现正确，则不会发生PDDL问题
//                                                if (true)
//                                                    isPDDL = false;
//                                            }
//                                        }
//
//                                        break;
//                                    }
//
//                                }
//                            }
//
//                        }
//
//                        // 检查保存集合元素的数据流
//                        if (eleB.getSourcesink()!=null && saveResults!=null){
//                            // eleB是保存元素，检查save的source
//
//                            for (int i = eleB.getSourcesink().size()-1;i>=0;i--){
//                                isCCDL = true;
//                                isPDDL = true; // 新定义，则再次置回true，表示默认有
//
//                                ISourceSinkDefinition sourceDefinition = eleB.getSourcesink().get(i);
//                                for (DataFlowResult saveResult : saveResults){
//                                    if ( sourceDefinition.equals( saveResult.getSource().getDefinition() ) ){
//                                        isCCDL = false;
//
//                                        // TODO 检查数据流去向，如果流入了viewmodel，则需要检查该sink对应的field会不会在viewmodel中得到进一步保存
//                                        ISourceSinkDefinition sink = saveResult.getSink().getDefinition();
//                                        if (sink instanceof MethodSourceSinkDefinition){
//                                            String sinkMethodSig = ((MethodSourceSinkDefinition) sink).getMethod().getSignature();
//                                            boolean isVmssd = vma.containsSourceSink(sinkMethodSig, false, true);
//                                            if (isVmssd){
//                                                // TODO 假设viewmodel实现正确，则不会发生PDDL问题
//                                                //if (true)
//                                                    isPDDL = false;
//                                            }
//                                        }
//                                        break;
//                                    }
//
//                                }
//                            }
//                        }
//
//                        if ( isCCDL || isPDDL ){    // 恢复和保存的所有定义都有数据流，才没有bug   00或者01或者11。不存在10，即不存在发生CCDL却不发生PDDL的情况
//                            // 输出bug
//                            handleIncorrectSaveRestoreWidget(eleA,eleB,bw,isCCDL,isPDDL); // 进入if分支，证明是01或者11，所以isPDDL一定是true
//                        }
//
//                    }
//                    else {  // 存在一个元素，没有source sink相关的定义，输出为bug
//                        handleIncorrectSaveRestoreWidget(eleA,eleB, bw,true,true);
//                    }
//
//
//
//                    // 这一对元素存在映射关系。为了不影响其他元素判断，将他们移除
//                    A.remove(indexA);
//                    B.remove(indexB);
//                    break;
//                }
//
//            }
//        }
//
//        // 存在映射关系的元素处理完毕，此时A,B中剩下的元素均是失配元素
//        for (indexA = A.size()-1; indexA>=0; indexA--){
//            DataVariable eleA = A.get(indexA);
//            handleNoSaveWidget(eleA,bw,true,true);
//        }
//
//        for (indexB = B.size()-1; indexB>=0; indexB--){
//            DataVariable eleB = B.get(indexB);
//            handleNoRestoreWidget(eleB,bw,true,true);
//        }


    }


    private boolean isSaveToVM(DataFlowResult flow) {
        ISourceSinkDefinition flowSink = flow.getSink().getDefinition();
        if (flowSink instanceof MethodSourceSinkDefinition){
            SootMethodAndClass sinkMethod = ((MethodSourceSinkDefinition) flowSink).getMethod();

            for (SootMethod vmSink : vma.getSinks()){
                if (vmSink.getSignature().equals(sinkMethod.getSignature()))
                    return true;
            }
        }

        return false;
    }

    private boolean isDirtySaveOfVM(DataFlowResult flow) {
        ISourceSinkDefinition flowSink = flow.getSink().getDefinition();
        if (flowSink instanceof MethodSourceSinkDefinition){
            SootMethodAndClass sinkMethod = ((MethodSourceSinkDefinition) flowSink).getMethod();

            for (SootMethod dirtySink : vma.getDirtySources()){
                if (dirtySink.getSignature().equals(sinkMethod.getSignature()))
                    return true;
            }
        }

        return false;
    }


    private boolean isRestoreFromVM(DataFlowResult flow) {
        ISourceSinkDefinition flowSource = flow.getSource().getDefinition();
        if (flowSource instanceof MethodSourceSinkDefinition){
            SootMethodAndClass sourceMethod = ((MethodSourceSinkDefinition) flowSource).getMethod();

            for (SootMethod vmSource : vma.getSources()){
                if (vmSource.getSignature().equals(sourceMethod.getSignature()))
                    return true;
            }
        }

        return false;
    }

    private boolean isDirtyRestoreOfVM(DataFlowResult flow) {
        ISourceSinkDefinition flowSource = flow.getSource().getDefinition();
        if (flowSource instanceof MethodSourceSinkDefinition){
            SootMethodAndClass sourceMethod = ((MethodSourceSinkDefinition) flowSource).getMethod();

            for (SootMethod dirtySource : vma.getDirtySources()){
                if (dirtySource.getSignature().equals(sourceMethod.getSignature()))
                    return true;
            }
        }

        return false;
    }

    private DataFlowResult isInSaveDataflow(ISourceSinkDefinition sourceDef) {
        if (saveResults==null)
            return null;
        for (DataFlowResult dataflow : saveResults){
            if (sourceDef.equals( dataflow.getSource().getDefinition())){
                return dataflow;
            }
        }
        return null;
    }

    private DataFlowResult isInRestoreDataflow(ISourceSinkDefinition sinkDefinition) {
        if (restoreResults==null)
            return null;
        for (DataFlowResult dataflow : restoreResults){
            if (sinkDefinition.equals( dataflow.getSink().getDefinition())){
                return dataflow;
            }
        }
        return null;
    }

    private void handleBugWidgetForRmap(Feature widgetFeature, List<DataVariable> RmapVars, List<DataVariable> SmapVars, BufferedWriter bw, int count) throws IOException {
        System.out.println("\n[ 控件+"+widgetFeature.toString()+"，识别不到对应需要保存的数据 ]");
        bw.write("No."+count+","+ProcessPunctuationForCsv(widgetFeature.toString()));
        bw.newLine();
        bw.write("R:");
        bw.newLine();
        for (DataVariable dv : RmapVars){
            bw.write(ProcessPunctuationForCsv("path= "+dv.getPath()+" : "+dv.getStmt()));
            bw.newLine();
        }
        bw.write("S:");
        bw.newLine();
        bw.write("--not found--");
        bw.newLine();
        bw.write("DL_CC="+true+",DL_PD="+true);
        bw.newLine();
        bw.newLine();
        bw.newLine();
    }

    private void handleBugWidgetForSmap(Feature widgetFeature, List<DataVariable> RmapVars, List<DataVariable> SmapVars, BufferedWriter bw, int count) throws IOException {
        System.out.println("\n[ 控件+"+widgetFeature.toString()+"，识别不到对应需要恢复的数据 ]");
        bw.write("No."+count+","+ProcessPunctuationForCsv(widgetFeature.toString()));
        bw.newLine();
        bw.write("R:");
        bw.newLine();
        bw.write("--not found--");
        bw.newLine();
        bw.write("S:");
        bw.newLine();
        for (DataVariable dv : SmapVars){
            bw.write(ProcessPunctuationForCsv("path= "+dv.getPath()+" : "+dv.getStmt()));
            bw.newLine();
        }
        bw.write("DL_CC="+true+",DL_PD="+true);
        bw.newLine();
        bw.newLine();
        bw.newLine();
    }

    private void handleIncorrectSaveRestoreWidget(Feature widgetFeature, List<DataVariable> RmapVars, List<DataVariable> SmapVars, BufferedWriter bw, boolean isCCDL, boolean isPDDL, int count) throws IOException {
        System.out.println("\n[ 控件"+widgetFeature.toString()+"，识别到保存和恢复操作，但数据值来源不正确 ]");
        bw.write("No."+count+","+ProcessPunctuationForCsv(widgetFeature.toString()));
        bw.newLine();
        bw.write("R:");
        bw.newLine();
        for (DataVariable dv : RmapVars){
            bw.write(ProcessPunctuationForCsv("path= "+ dv.getPath()+" : "+dv.getStmt()));
            bw.newLine();
        }
        bw.write("S:");
        bw.newLine();
        for (DataVariable dv : SmapVars){
            bw.write(ProcessPunctuationForCsv("path= "+dv.getPath()+" : "+dv.getStmt()));
            bw.newLine();
        }
        bw.write("DL_CC="+isCCDL+",DL_PD="+isPDDL);
        bw.newLine();
        bw.newLine();
        bw.newLine();
    }


    private static String ProcessPunctuationForCsv(String srcStr)
    {
        boolean quoteFlag = false;//是否添加过双引号
        //如果存在双引号，需要将字符串的一个双引号 替换为 两个双引号。并且需要在字符串的前后加上双引号
        if (srcStr.contains("\""))
        {
            srcStr = srcStr.replace("\"", "\"\"");
            srcStr = "\"" + srcStr + "\"";
            quoteFlag = true;
        }
        //如果只存在逗号（不存在引号），将前后加引号即可
        if (srcStr.contains(",") && !quoteFlag)
        {
            srcStr = "\"" + srcStr + "\"";
        }
        return srcStr;
    }
}
