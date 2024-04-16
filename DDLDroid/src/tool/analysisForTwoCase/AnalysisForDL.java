package tool.analysisForTwoCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.sourcesSinks.definitions.*;
import soot.jimple.infoflow.sourcesSinks.definitions.StatementSourceSinkDefinition;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.*;
import soot.util.Chain;
import tool.basicAnalysis.AppParser;
import tool.basicAnalysis.ViewModelAnalyzer;
import tool.componentStructure.ActivityLayoutComponentTree;
import tool.guiForAnalysis.ResultAnalysis;
import tool.staticGuiModel.Widget;
import tool.utils.ClassService;
import tool.utils.MethodService;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnalysisForDL { //case 1 data loss detection

    private List<SootClass> classesForActivity;
    private List<SootClass> classesForFragment;
    private CallGraph callGraph;
    private String resultDir;

    /**有恢复的集合*/
    private Map<Feature, List<DataVariable>> Rmap;
    private List<DataVariable> dataVariableA;
    /**待保存的集合*/
    private Map<Feature, List<DataVariable>> Smap;
    private List<DataVariable> dataVariableB;

    private SaveFlowSourceSinkDefinitionProvider saveFlowSourceSinkDefinitionProvider;
    private RestoreFlowSourceSinkDefinitionProvider restoreFlowSourceSinkDefinitionProvider;

    private SetupApplication setupApplication;
    private ViewModelAnalyzer vma;
    private ResultAnalysis out;

    private static final Logger logger = LoggerFactory.getLogger(InfoflowResults.class);

    //////////////////////////////////////////////构造函数初始化成员变量////////////////////////////////////////////////////////////

    public AnalysisForDL(List<SootClass> activityClasses, List<SootClass> fragmentClasses, CallGraph callGraph, String resultDir) throws IOException {
        this.classesForActivity = activityClasses;
        this.classesForFragment = fragmentClasses;
        this.callGraph = callGraph;
        this.resultDir = resultDir;

        Rmap = new HashMap<>();
        dataVariableA = new ArrayList<>();
        Smap = new HashMap<>();
        dataVariableB = new ArrayList<>();

        vma = new ViewModelAnalyzer(AppParser.v().getViewmodels());
        vma.fun();

        saveFlowSourceSinkDefinitionProvider = SaveFlowSourceSinkDefinitionProvider.newSaveFlow("./SourcesAndSinks.txt");
        showSourceSinkDefinition("初始化前的saveFlowProvider：", saveFlowSourceSinkDefinitionProvider);

        restoreFlowSourceSinkDefinitionProvider = RestoreFlowSourceSinkDefinitionProvider.newRestoreFlow("./SourcesAndSinks.txt");
        showSourceSinkDefinition("初始化前的restoreFlowProvider：", restoreFlowSourceSinkDefinitionProvider);

        //TestForSourceSinkDefinition();

    }


    //////////////////////////////////////////////缺陷检测总体流程部分////////////////////////////////////////////////////////////

    /**
     * 以UI控制器为单位进行检测，寻找数据丢失缺陷*/
    public void detectActivity(long startForOneApp) throws IOException {

        List<SootClass> actAndFrags = new ArrayList<SootClass>();
        actAndFrags.addAll(classesForActivity);
        actAndFrags.addAll(classesForFragment);
        for (SootClass activity : actAndFrags){ // classesForActivity
            System.out.println("\n>>>类名: "+activity.getName());
            if (ClassService.isDialogFragment(activity))    // system maintain commitloss?
                continue;
            // UI控制器初始化的生命周期回调。用户交互注册通常在这些回调里。
            List<SootMethod> inits = new ArrayList<>();
            String[] initCallbacks = new String[]{"onCreate", "onCreateView", "onCreateDialogView", "onCreateDialog", "onActivityCreated", "onRestart", "onStart", "onResume"};//"onAttach"
            for (int i = 0; i < initCallbacks.length; i++) {
                try{
                    SootMethod initCallback = activity.getMethodByNameUnsafe(initCallbacks[i]);
                    if (initCallback!=null){
                        inits.add(initCallback);
                    }
                }catch (AmbiguousMethodException ambiguousMethodException){
                    System.out.println("ambiguous Method");
                }

            }
            if(!isact(activity))
                continue;
//            for (SootMethod sootMethod : activity.getMethods()){
//                if (sootMethod.getName().equals("onCreate")
//                        || sootMethod.getName().equals("onCreateView")
//                        || sootMethod.getName().equals("onCreateDialogView")
//                        || sootMethod.getName().equals("onCreateDialog")
//                        || sootMethod.getName().equals("onActivityCreated")
//                        || sootMethod.getName().equals("onRestart")
//                        || sootMethod.getName().equals("onStart")
//                        || sootMethod.getName().equals("onResume")){
//                    inits.add(sootMethod);
//                }
//            }

            //  1、恢复集合。初始化stmtSourcesDefinitionForRestore
            //  分析初始化代码（非用户监听部分），即“恢复”操作
            for (SootMethod initMethod : inits){
                // 检测 “恢复”操作
                String initPath = activity.getName();
                detectUIRecovery(activity.getName(), initPath, initMethod, null, false,new LinkedList<SootField>());
            }

            //  2 保存集合。初始化stmtSinksDefinitionForSave
            //  2.1 寻找和分析注册用户监听事件代码。即，分析初始化代码寻找用户监听，并分析交互响应回调方法对数据的更改。
            for (SootMethod initMethod : inits){    // set in init lifecycle callbacks
                String initPath = activity.getName();
                detectUIchangeForSave(activity.getName(), initMethod, initPath, null);
            }
            //  2.2 查询构建的组件树，set in xml files with Android:onClick . 这部分是在xml文件中定义和注册的交互回调。
            Set<String> methodNames = findStaticEventRegisteration(activity);
            for (String name : methodNames){
                SootMethod eventMethod = activity.getMethodByNameUnsafe(name);
                if (eventMethod!=null)
                    handleListener(activity.getName(), eventMethod, activity.getName(), null, new LinkedList<>());
            }

            //  2.3 往S集合中加入动态的EditText
            for (SootField field : activity.getFields()){
                if ( Scene.v().containsClass(field.getType().toString()) ){
                    SootClass fieldTypeClass = Scene.v().loadClass(field.getType().toString(), SootClass.BODIES);
                    if (ClassService.isView(fieldTypeClass)){   // 是不是View类的域?
                        handleEditViewField(activity, field, fieldTypeClass);
                    }
                }

            }

            //  2.4、分析菜单点击事件。这也算用户交互监听。  public boolean onOptionsItemSelected(@NonNull MenuItem item){} 生命周期回调  onMenuItemClick
            SootMethod onOptionsItemSelected = activity.getMethodByNameUnsafe("onOptionsItemSelected");
            SootMethod onMenuItemClick = activity.getMethodByNameUnsafe("onMenuItemClick");
            if (onOptionsItemSelected != null){
                String initPath = activity.getName();
                System.out.println("\n找到菜单点击回调onOptionsItemSelected(): "+initPath+" : "+onOptionsItemSelected.getSignature());
                handleMenuOptionsListener(activity.getName(), onOptionsItemSelected, initPath);
            }
            if (onMenuItemClick != null){
                String initPath = activity.getName();
                System.out.println("\n找到菜单点击回调onMenuItemClick(): "+initPath+" : "+onMenuItemClick.getSignature());
                handleMenuOptionsListener(activity.getName(), onMenuItemClick, initPath);
            }

            // 2.5、如果是preferenceUI
            boolean isPrefs = ClassService.isPrefsUI(activity) || ClassService.isPrefsUISupportV7(activity);
            if (isPrefs){
                PreferencesAnalyzer prefsAna = new PreferencesAnalyzer(activity);
                List<AXmlNode> prefsDialogList = prefsAna.doAnalysis();
                System.out.println(prefsDialogList);
                //加入S
                for (AXmlNode node : prefsDialogList){
                    DataVariable dtB = new DataVariable();

                    System.out.println("需要保存: find a dialog");
                    Type type = Scene.v().getTypeUnsafe(node.getTag());
                    if (type!=null) {
                        dtB.setWidgetType(type);    //1
                        dtB.setPath(activity.getName());  //2
                        dtB.setStmt(null);  //3

                        // 用各种属性设置作为特征
                        Feature feature = new Feature(activity.getName(), type, node.getAttributes());
                        dtB.setFeature(feature); //4

                        dtB.setDependency(null);    //5

                        // 3,划分集合元素
                        if (Smap.containsKey(feature)) {
                            Smap.get(feature).add(dtB);
                        } else {
                            List<DataVariable> list = new ArrayList<>();
                            list.add(dtB);
                            Smap.put(feature, list);
                        }

                        dataVariableB.add(dtB);
                    }
                }


            }

            handleComplex(activity);

        }

        //testMap();
        // IR分析完毕，构建了相应的source和sink，运行数据流构建
        InfoflowResults[] results = myRunInfoflow();    //  results[0] = saveResult;    results[1] = restoreResult;

        out = new ResultAnalysis(Rmap, Smap, results[0], results[1], vma, startForOneApp);
        out.analysisOut(resultDir);

    }

    /***/
    private Set<String> findStaticEventRegisteration(SootClass activity) {
        Set<String> eventMethods = new HashSet<>();
        List<ActivityLayoutComponentTree> list = AppParser.v().getActivityComponentTrees();
        for (ActivityLayoutComponentTree act : list){
            if (act.getActivityName().equals(activity.getName())){
                Widget widget = act.getRootWidget();
                Queue<Widget> queue = new LinkedList<>();
                queue.offer(widget);
                while(!queue.isEmpty()){
                    widget = queue.poll();
                    if (widget.getClickMethodName()!=null)
                        eventMethods.add(widget.getClickMethodName());
                    if (widget.getChildren()!=null && widget.getChildren().size()!=0)
                        queue.addAll(widget.getChildren());
                }

                // 找到对应的组件树，分析完break
                break;
            }

        }
        return eventMethods;
    }

    /**在当前UI类的onPause回调中检测符合要求的EditText类的getText()调用点作为source*/
    private void handleEditViewField(SootClass actClass, SootField widgetField, SootClass fieldTypeClass) {
        SootMethod onPause = actClass.getMethodByNameUnsafe("onPause");
        LinkedList<SootField> fieldList = new LinkedList<>();
        // 直接是动态的EditText
        if (ClassService.isEditText(fieldTypeClass) && !isStaticBindWidgetInActivityOrFragment(actClass.getName(), widgetField)){
            fieldList.add(widgetField);
            handleOnPauseForEditText(actClass, onPause, fieldList);
        }
        // TextView的多态赋值版EditText
        else if (ClassService.isTextView(fieldTypeClass) && isEditTextPolymorphism(actClass, widgetField) && !isStaticBindWidgetInActivityOrFragment(actClass.getName(), widgetField)){
            fieldList.add(widgetField);
            handleOnPauseForEditText(actClass, onPause, fieldList);
        }
        // 封装的layout类里有的EditText
        else if (ClassService.isViewGroup(fieldTypeClass) && !isStaticBindWidgetInActivityOrFragment(actClass.getName(), widgetField)){
            fieldList.add(widgetField);
            for (SootField subField : fieldTypeClass.getFields()){
                if ( Scene.v().containsClass(subField.getType().toString()) ){
                    SootClass typeClass = Scene.v().loadClass(subField.getType().toString(), SootClass.SIGNATURES);
                    if (ClassService.isEditText(typeClass)){
                        fieldList.add(subField);
                        handleOnPauseForEditText(actClass, onPause, fieldList);
                    }
                    else if(ClassService.isTextView(typeClass) && isEditTextPolymorphism(fieldTypeClass, subField)){
                        fieldList.add(subField);
                        handleOnPauseForEditText(actClass, onPause, fieldList);
                    }
                }
            }
        }
    }

    /**TextView是否是多态赋值EditText,即强转，表面是TextView，实际上是EditText*/
    private boolean isEditTextPolymorphism(SootClass sootClass, SootField widgetField) {
        SootMethod sootMethod = null;
        if (ClassService.isActivity(sootClass))
            sootMethod = sootClass.getMethodByNameUnsafe("onCreate");
        else if (ClassService.isFragment(sootClass))
            sootMethod = sootClass.getMethodByNameUnsafe("onCreateView");
        else if (ClassService.isView(sootClass)){
            for (SootMethod sm : sootClass.getMethods())
                if (sm.isConstructor()){
                    sootMethod = sm;
                    break;
                }
        }

        if (sootMethod!=null && MethodService.tryRetrieveActiveBody(sootMethod)){
            for (Unit unit : sootMethod.retrieveActiveBody().getUnits()){
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                    Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                    SootField field = stmt.getFieldRef().getField();
                    if (leftOp.toString().contains(field.toString()) && field.equals(widgetField)){ // widgetField域的赋值语句
                        Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                        if (Scene.v().containsClass(rightOp.getType().toString())){ // 检查右操作数的类型
                            if (ClassService.isEditText( Scene.v().loadClass(rightOp.getType().toString(), SootClass.SIGNATURES) ))
                                return true;
                        }

                        break;
                    }
                }
            }
        }
        return false;
    }

    /**尝试在onPause回调中，为非静态资源id号绑定的EditText建模*/
    private void handleOnPauseForEditText(SootClass actClass, SootMethod onPause, LinkedList<SootField> widgetField) {

        DataVariable dtB = new DataVariable();

        SootField lastWidget = widgetField.get(widgetField.size()-1);
        dtB.setWidgetType(lastWidget.getType());//1
        if (onPause!=null){
            findGetTextInOnPause(dtB, onPause, actClass.getName(), lastWidget);
        }
        dtB.setPath(actClass.getName());
        Feature feature = new Feature(actClass.getName(), lastWidget.getType(), widgetField);
        dtB.setFeature(feature);//4
        dtB.setDependency(null);//5

        if (Smap.containsKey(feature)){
            Smap.get(feature).add(dtB);
        }
        else{
            List<DataVariable> list = new ArrayList<>();
            list.add(dtB);
            Smap.put(feature,list);
        }

        dataVariableB.add(dtB);

    }

    /**尝试在onPause回调中，为已经建模的EditText 寻找getText()调用，并将其定义为source
     * @param onPause 指onPause方法，及其调用链上的callee*/
    private Stmt findGetTextInOnPause(DataVariable dtB, SootMethod onPause, String path, SootField lastWidget) {
        String nowpath = path + " -> " + onPause.getSignature();
        List<SootMethod> callees = getCalleesFrom(onPause);
        if (onPause!=null && MethodService.tryRetrieveActiveBody(onPause)){
            for (Unit unit : onPause.retrieveActiveBody().getUnits()){
                UnitGraph unitGraph = new ExceptionalUnitGraph(onPause.retrieveActiveBody());
                MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱
                Stmt stmt = (Stmt) unit;

                if (stmt.containsInvokeExpr()){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    SootMethod callee = invokeExpr.getMethod();

                    if ((ClassService.isEditText(callee.getDeclaringClass()) || ClassService.isTextView(callee.getDeclaringClass())) && callee.getName().equals("getText")){
                        if (stmt instanceof DefinitionStmt ){
                            if (invokeExpr instanceof InstanceInvokeExpr){
                                Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                                List<Unit> preunits = dFinder.getDominators(stmt);
                                for (int i= preunits.size()-1; i>=0;i--){
                                    Stmt prestmt = (Stmt) preunits.get(i);
                                    if (prestmt instanceof DefinitionStmt && prestmt.containsFieldRef())
                                        if (((DefinitionStmt) prestmt).getLeftOp().equivTo(base) && prestmt.getFieldRef().getField().equals(lastWidget)){

                                            Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                                            dtB.setPath(nowpath);
                                            dtB.setStmt(stmt);
                                            dtB.addSourceSink( addSourceForSave(stmt, (Local) leftOp, null) );

                                            break;
                                        }
                                }


                            }

                        }
                    }
                    else{   // 普通调用
                        if (nowpath.contains(callee.getSignature()))    // 截止当前方法的路径，已经包含了当前调用语句的callee，则不重复检测
                            continue;
                        if (callees.contains(callee)||callee.getDeclaringClass().getName().contains(AppParser.v().getPkg())){
                            findGetTextInOnPause(dtB, callee, nowpath, lastWidget);
                        }
                    }

                }

            }
        }
        return null;
    }


    //////////////////////////////////////////////用于检测数据恢复并定义【恢复】相关sink点////////////////////////////////////////////////////////////

    List<Stmt> callSitesForRestore = new ArrayList<>();

    /**
     * 检查应用初始化时的所有操作，分析数据恢复 */
    private void detectUIRecovery( String activityName, String prePath, SootMethod initMethod, Stmt callsite, boolean isInstanceInvoke, LinkedList<SootField> baseFields) {

        if (!MethodService.tryRetrieveActiveBody(initMethod))
            return;
        /* 当前所在的待分析的方法initMethod; 假设是B();
        * 调用路径是iniPath e.g., activityName --> A() --> B()
        * callsite指的是A()方法中的哪条语句调用触发到了B()，即 A() -callsite-> B()*/

        List<SootMethod> callees = getCalleesFrom(initMethod);

        String initPath = prePath +" -> "+ initMethod.getSignature();
        callSitesForRestore.add(callsite); // 添加到队尾，表示当前B方法由前者A方法中的callsite语句调用的


        Body body = initMethod.retrieveActiveBody();    // initMethod.getActiveBody();
        UnitGraph unitGraph = new ExceptionalUnitGraph(body);
        MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph);  // 反支配者，其必经过的后驱
        MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱


        for (Unit unit : body.getUnits()){
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()){
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                SootMethod callee = invokeExpr.getMethod();
                int kind = isWidgetSetting(callee);
                if (kind!=0){
                    // !=0, 证明一定是一个考虑范围内的 控件API调用
                    //System.out.println(sootMethod.getDeclaringClass());
                    //dtA.setWidgetType(callee.getDeclaringClass().toString());
                    DataVariable dtA = new DataVariable();

                    List<Unit> pres = null;    // 该调用语句的支配者们
                    if (invokeExpr instanceof StaticInvokeExpr)
                        continue;
                    InstanceInvokeExpr instanceInvokeExpr = ((InstanceInvokeExpr) invokeExpr);  // 该调用语句通常是一个实际的控件对象在调用
                    Value baseObject = null;   // instanceInvokeExpr中的实际控件对象

                    SootField widgetField = null;
                    WidgetSetting dialogSettings = null;
                    Feature feature = null;

                    ControlDependency dependency = null;

                    switch (kind){
                        case 1: // textView.setText();
                            System.out.println("恢复阶段: find a TextView");
                            // 1,找到这句话的调用变量，向上追踪是哪个控件，通过ID号
                            baseObject = instanceInvokeExpr.getBase();
                            dtA.setWidgetType(baseObject.getType()); //1
                            dtA.setPath(initPath);  //2
                            dtA.setStmt(stmt);  //3

                            // 向上追溯调用此方法的对象的赋值,尝试找到textView的域控件，用以作为特征
                            pres = dFinder.getDominators(stmt);
                            widgetField = findWidgetField(pres, baseObject);
                            if (widgetField == null){
                                System.out.println("\t"+baseObject+" 没有找到对应的TextView");
                                break;
                            }
                            else {
                                LinkedList fatherList = new LinkedList<>(baseFields);
                                fatherList.add(widgetField);
                                feature = new Feature(activityName, baseObject.getType(), fatherList);
                                dtA.setFeature(feature); //4
                            }
                            dtA.setDependency(null);//5

                            // 2,找到setText的参数。利用StatementSourceSinkDefinition建模为Sink点
                            Value stringPara = invokeExpr.getArg(0);
                            if (body.getLocals().contains(stringPara)){
                                dtA.addSourceSink( addSinkForRestore(stmt, (Local) stringPara ,null) );   //6
                            }

                            // 3,划分集合元素
                            if (Rmap.containsKey(feature)){
                                Rmap.get(feature).add(dtA);
                            }
                            else{
                                List<DataVariable> list = new ArrayList<>();
                                list.add(dtA);
                                Rmap.put(feature,list);
                            }

                            dataVariableA.add(dtA);
                            break;
                        case 5: // editText.setText();
                            System.out.println("恢复阶段: find a EditText");
                            // 1,找到这句话的调用变量，向上追踪是哪个控件，通过ID号
                            baseObject = instanceInvokeExpr.getBase();
                            dtA.setWidgetType(baseObject.getType()); //1
                            dtA.setPath(initPath);  //2
                            dtA.setStmt(stmt);  //3

                            // 向上追溯调用此方法的对象的赋值,尝试找到textView的域控件，用以作为特征
                            pres = dFinder.getDominators(stmt);
                            widgetField = findWidgetField(pres, baseObject);
                            if (widgetField == null){
                                System.out.println("\t"+baseObject+" 没有找到对应的EditText");
                                break;
                            }
                            else if(isStaticBindWidgetInActivityOrFragment(activityName, widgetField)){
                                System.out.println("\t"+baseObject+" 静态绑定EditText");
                                break;
                            }
                            else {
                                LinkedList fatherList2 = new LinkedList<>(baseFields);
                                fatherList2.add(widgetField);
                                feature = new Feature(activityName, baseObject.getType(), fatherList2);
                                dtA.setFeature(feature); //4
                                // 如果是在UI类里静态绑定的EditText，可以由系统实现保存恢复，不加入待检测R集合。
                                if (isStaticBindWidgetInActivityOrFragment(activityName,widgetField)){
                                    break;
                                }
                            }
                            dtA.setDependency(null);//5

                            // 2,找到setText的参数。利用StatementSourceSinkDefinition建模为Sink点
                            Value stringPara2 = invokeExpr.getArg(0);
                            if (body.getLocals().contains(stringPara2)){
                                dtA.addSourceSink( addSinkForRestore(stmt, (Local) stringPara2 ,null) );   //6
                            }

                            // 3,划分集合元素
                            if (Rmap.containsKey(feature)){
                                Rmap.get(feature).add(dtA);
                            }
                            else{
                                List<DataVariable> list = new ArrayList<>();
                                list.add(dtA);
                                Rmap.put(feature,list);
                            }

                            dataVariableA.add(dtA);
                            break;
                        case 2:
                            //System.out.println("android.app.Dialog");
                            //找到dialog.show();
                            //TODO 如果是一个启动时默认弹出，并根据用户交互的结果来决定下次启动是否需要再次弹出的呢？这种不会被识别到B集合中，即仅在A集合中有，B集合中没有的。
                            System.out.println("恢复阶段: find a android.app.Dialog");
                            baseObject = instanceInvokeExpr.getBase();  // baseObject就是当前的对话框控件
                            dtA.setWidgetType(baseObject.getType()); //1
                            dtA.setPath(initPath);//2
                            dtA.setStmt(stmt);//3

                            pres = dFinder.getDominators(stmt);
                            dialogSettings = new WidgetSetting(findDialogSettings(pres,baseObject));
                            feature = new Feature(activityName,baseObject.getType(),dialogSettings);
                            dtA.setFeature(feature); //4

                            //  1,寻找控制依赖d; 若找到，则将其赋值语句作为Sink;
                            dependency = findDependency(initPath,initMethod,stmt,callSitesForRestore.size()-1);
                            dtA.setDependency(dependency); //5
                            if (dependency !=null){
                                // 这里直接用ifStmt作为sink了，并未向上溯源赋值
                                dtA.addSourceSink( addSinkForRestore(dependency.getIfStmt(), (Local) dependency.getConditionLocal() ,null) ); //6
                            }
                            /*恢复对话框不需要判断它的setOnShowListener和setOnDismissListener
                            * 因为这本质上是一种用户交互带来界面改变的功能逻辑
                            * 恢复只需要根据之前的交互形成的状态信息数据来恢复即可*/

                            // 3,划分集合元素
                            if (Rmap.containsKey(feature)){
                                Rmap.get(feature).add(dtA);
                            }
                            else{
                                List<DataVariable> list = new ArrayList<>();
                                list.add(dtA);
                                Rmap.put(feature,list);
                            }

                            dataVariableA.add(dtA);
                            break;
                        case 3:
                            //System.out.println("androidx.drawerlayout.widget.DrawerLayout");
                            // 只判断是否有打开openDrawer()即可，因为默认就是关闭的
                            System.out.println("恢复阶段: find a androidx.drawerlayout.widget.DrawerLayout");
                            baseObject = instanceInvokeExpr.getBase();  // baseObject就是当前的菜单控件
                            dtA.setWidgetType(baseObject.getType());//1
                            dtA.setPath(initPath);//2
                            dtA.setStmt(stmt);//3

                            // 向上追溯调用此方法的对象的赋值,尝试找到textView的id号，用以作为特征
                            pres = dFinder.getDominators(stmt);
                            widgetField = findWidgetField(pres, baseObject);
                            if (widgetField == null){
                                System.out.println("\t"+baseObject+" 没有找到对应的DrawerLayout");
                            }
                            else {
                                LinkedList drawFather = new LinkedList<>(baseFields);
                                drawFather.add(widgetField);
                                feature = new Feature(activityName, baseObject.getType(), drawFather);
                                dtA.setFeature(feature);//4
                            }

                            // 寻找控制依赖d
                            dependency = findDependency(initPath,initMethod,stmt,callSitesForRestore.size()-1);
                            dtA.setDependency(dependency); //5
                            if (dependency !=null){
                                // 这里直接用ifStmt作为sink了，并未向上溯源赋值
                                dtA.addSourceSink( addSinkForRestore(dependency.getIfStmt(), (Local) dependency.getConditionLocal() ,null) ); //6
                            }

                            // 3,划分集合元素
                            if (Rmap.containsKey(feature)){
                                Rmap.get(feature).add(dtA);
                            }
                            else{
                                List<DataVariable> list = new ArrayList<>();
                                list.add(dtA);
                                Rmap.put(feature,list);
                            }

                            dataVariableA.add(dtA);
                            break;
                        case 4:
                            //System.out.println("androidx.appcompat.app.AlertDialog");
                            System.out.println("恢复阶段: find a androidx.appcompat.app.AlertDialog");
                            baseObject = instanceInvokeExpr.getBase();  // baseObject就是当前的对话框控件
                            dtA.setWidgetType(baseObject.getType());//1
                            dtA.setPath(initPath);//2
                            dtA.setStmt(stmt);//3

                            // 使用dialog的各种属性设置API作为用于比较的特征
                            pres = dFinder.getDominators(stmt);
                            dialogSettings = new WidgetSetting(findDialogSettings(pres,baseObject));
                            feature = new Feature(activityName, baseObject.getType(), dialogSettings);
                            dtA.setFeature(feature); //4

                            //  1,寻找控制依赖d; 若找到，则将其赋值语句作为Sink;
                            dependency = findDependency(initPath,initMethod,stmt,callSitesForRestore.size()-1);
                            dtA.setDependency(dependency); //5
                            if (dependency !=null){
                                // 这里直接用ifStmt作为sink了，并未向上溯源赋值
                                dtA.addSourceSink( addSinkForRestore(dependency.getIfStmt(), (Local) dependency.getConditionLocal() ,null) ); //6
                            }
                            /*恢复对话框不需要判断它的setOnShowListener和setOnDismissListener
                             * 因为这本质上是一种用户交互带来界面改变的功能逻辑
                             * 恢复只需要根据之前的交互形成的状态信息数据来恢复即可*/

                            // 3,划分集合元素
                            if (Rmap.containsKey(feature)){
                                Rmap.get(feature).add(dtA);
                            }
                            else{
                                List<DataVariable> list = new ArrayList<>();
                                list.add(dtA);
                                Rmap.put(feature,list);
                            }

                            dataVariableA.add(dtA);
                            break;
                        default:
                            System.out.println();
                    }

                }   // 检测到是控件相关的API调用的分析完成
                else{
                    if (initPath.contains(callee.getSignature()))
                        continue;
                    if (callees.contains(callee)){
                        if (invokeExpr instanceof InstanceInvokeExpr) { // 实例调用，区分对象敏感性
                            Value baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                            List<Unit> pres = dFinder.getDominators(stmt);
                            for (int i = pres.size()-1; i>=0; i--){
                                Stmt stmt1 = (Stmt) pres.get(i);
                                if (stmt1 instanceof DefinitionStmt && ((DefinitionStmt) stmt1).getLeftOp().equivTo(baseObject))
                                {
                                    if (stmt1.containsFieldRef()) {
                                        Value rightOp = ((DefinitionStmt) stmt1).getRightOp();
                                        if (rightOp.toString().contains(stmt1.getFieldRef().getField().toString())) {
                                            LinkedList list = new LinkedList<>(baseFields);
                                            list.add(stmt1.getFieldRef().getField());
                                            detectUIRecovery(activityName, initPath, callee, stmt, true, list);   // 当前路径initPath下的stmt语句是一句调用，调用了方法callee;
                                            break;
                                        }
                                    }
                                    else if (((DefinitionStmt) stmt1).getRightOp() instanceof ParameterRef || ((DefinitionStmt) stmt1).getRightOp() instanceof ThisRef){
                                        LinkedList list = new LinkedList<>(baseFields);
                                        detectUIRecovery(activityName, initPath, callee, stmt, true, list);
                                        break;
                                    }
                                }
                            } // 找不到控件域变量的话，则放弃分析，就想分析只会导致误报
                            //detectUIRecovery(initPath, callee, stmt, true, baseFields);   // 当前路径initPath下的stmt语句是一句调用，调用了方法callee;
                        }
                        else
                            detectUIRecovery(activityName, initPath, callee, stmt, false, baseFields);
                    }
                }   // 普通的调用继续深度优先分析完成
            }   // 逐条分析代码体的语句
        }   // 遍历完当前待分析的initMethod的所有语句

        // 是不是要从callSitesForRestore中remove一个 ? 是
        callSitesForRestore.remove(callSitesForRestore.size()-1); // 移除队尾
        /*例如 activityName -null-> A() -callsite-> B(); callSitesForRestore中的队尾元素就是callsite
        * 当前是在B()，且B已经分析完；
        * 即将返回到上层A方法的"逐条分析代码体的语句"循环中else分支结束的地方，然后根据循环继续向下分析剩余语句；
        * 剩余语句中可能还存在另一条包含调用的stmt，将调用C()方法，即 activityName -null-> A() -stmt-> C();
        * 此时将触发"逐条分析代码体的语句"循环中的else分支，并深度优先递归调用detectUIRecovery(initPath, callee, stmt);
        * 然后转入分析C方法，并在此方法一开始运行时将传入的stmt作为callsite添加到callSitesForRestore的队尾；
        * 表示当前C方法由上层A方法中的该语句调用而来；
        * 同理，分析完C方法后，将此stmt从callSitesForRestore队尾移除 */

    }

    /** 检查是否是注册在当前UI组件里的通过findViewById()静态捆绑的控件。动态封装的控件，使用时再注册添加到UI的话，即便是findViewById绑定，也不唯一
     * @return 返回对应的id号，若不是的则返回-1
     * */
    private boolean isStaticBindWidgetInActivityOrFragment(String activityName, SootField widgetField) {
        SootClass sootClass = widgetField.getDeclaringClass();
        if (ClassService.isActivity(sootClass) && sootClass.getName().contains(activityName)){
            SootMethod onCreate = sootClass.getMethodByNameUnsafe("onCreate");
            if (onCreate!=null && MethodService.tryRetrieveActiveBody(onCreate)){
                Body body = onCreate.retrieveActiveBody();
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                        Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                        Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                        SootField field = stmt.getFieldRef().getField();
                        if (widgetField.equals(field) && leftOp.toString().contains(field.toString()) ){    // widgetField = local;
                            List<Unit> pres = getPrevUnits(stmt, body);
                            int limit = Math.min(pres.size(), 4);
                            for (int i = 1;i<limit;i++){
                                Stmt pre = (Stmt) pres.get(i);
                                if (pre instanceof DefinitionStmt && ((DefinitionStmt) pre).getLeftOp().equivTo(rightOp)){
                                    rightOp = ((DefinitionStmt) pre).getRightOp();
                                    if (rightOp instanceof CastExpr)
                                        rightOp = ((CastExpr) rightOp).getOp();
                                    if (rightOp instanceof InvokeExpr){
                                        if ( ((InvokeExpr) rightOp).getMethod().getName().equals("findViewById") )
                                            return true;
                                    }
                                }
                            }
                            break;  // 确实找到了域的赋值语句，但分析后没有return，即不是静态绑定的，那么就不用继续往下找了
                        }
                    }
                }
            }
        }
        else if (ClassService.isFragment(sootClass) && sootClass.getName().contains(activityName)){
            SootMethod onCreateView = sootClass.getMethodByNameUnsafe("onCreateView");
            if (onCreateView!=null && MethodService.tryRetrieveActiveBody(onCreateView)){
                Body body = onCreateView.retrieveActiveBody();
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                        Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                        Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                        SootField field = stmt.getFieldRef().getField();
                        if (widgetField.equals(field) && leftOp.toString().contains(field.toString()) ){    // widgetField = local;
                            List<Unit> pres = getPrevUnits(stmt, body);
                            int limit = Math.min(pres.size(), 4);
                            for (int i = 1; i<pres.size(); i++){  //pres.size() or limit
                                Stmt pre = (Stmt) pres.get(i);
                                if (pre instanceof DefinitionStmt && ((DefinitionStmt) pre).getLeftOp().equivTo(rightOp)){
                                    rightOp = ((DefinitionStmt) pre).getRightOp();
                                    if (rightOp instanceof CastExpr)
                                        rightOp = ((CastExpr) rightOp).getOp();
                                    if (rightOp instanceof InvokeExpr){
                                        if ( ((InvokeExpr) rightOp).getMethod().getName().equals("findViewById") )
                                            return true;
                                    }
                                }
                            }
                            break;  // 确实找到了域的赋值语句，但分析后没有return，即不是静态绑定的，那么就不用继续往下找了
                        }
                    }
                }
            }
            SootMethod onCreateDialogView = sootClass.getMethodByNameUnsafe("onCreateDialogView");
            if (onCreateDialogView!=null && MethodService.tryRetrieveActiveBody(onCreateDialogView)){
                Body body = onCreateDialogView.retrieveActiveBody();
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                        Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                        Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                        SootField field = stmt.getFieldRef().getField();
                        if (widgetField.equals(field) && leftOp.toString().contains(field.toString()) ){    // widgetField = local;
                            List<Unit> pres = getPrevUnits(stmt, body);
                            int limit = Math.min(pres.size(), 4);
                            for (int i = 1; i<limit; i++){
                                Stmt pre = (Stmt) pres.get(i);
                                if (pre instanceof DefinitionStmt && ((DefinitionStmt) pre).getLeftOp().equivTo(rightOp)){
                                    rightOp = ((DefinitionStmt) pre).getRightOp();
                                    if (rightOp instanceof CastExpr)
                                        rightOp = ((CastExpr) rightOp).getOp();
                                    if (rightOp instanceof InvokeExpr){
                                        if ( ((InvokeExpr) rightOp).getMethod().getName().equals("findViewById") )
                                            return true;
                                    }
                                }
                            }
                            break;  // 确实找到了域的赋值语句，但分析后没有return，即不是静态绑定的，那么就不用继续往下找了
                        }
                    }
                }
            }
            SootMethod onCreateDialog = sootClass.getMethodByNameUnsafe("onCreateDialog");
            if (onCreateDialog!=null && MethodService.tryRetrieveActiveBody(onCreateDialog)){
                Body body = onCreateDialog.retrieveActiveBody();
                for (Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt instanceof DefinitionStmt && stmt.containsFieldRef()){
                        Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                        Value rightOp = ((DefinitionStmt) stmt).getRightOp();
                        SootField field = stmt.getFieldRef().getField();
                        if (widgetField.equals(field) && leftOp.toString().contains(field.toString()) ){    // widgetField = local;
                            List<Unit> pres = getPrevUnits(stmt, body);
                            int limit = Math.min(pres.size(), 4);
                            for (int i = 1; i<limit; i++){
                                Stmt pre = (Stmt) pres.get(i);
                                if (pre instanceof DefinitionStmt && ((DefinitionStmt) pre).getLeftOp().equivTo(rightOp)){
                                    rightOp = ((DefinitionStmt) pre).getRightOp();
                                    if (rightOp instanceof CastExpr)
                                        rightOp = ((CastExpr) rightOp).getOp();
                                    if (rightOp instanceof InvokeExpr){
                                        if ( ((InvokeExpr) rightOp).getMethod().getName().equals("findViewById") )
                                            return true;
                                    }
                                }
                            }
                            break;  // 确实找到了域的赋值语句，但分析后没有return，即不是静态绑定的，那么就不用继续往下找了
                        }
                    }
                }
            }
        }

        // dagger 框架开发，无findviewbyid()调用，所以绑定的EditText需要排除
        if(isDaggerBindInActivityOrFragment(widgetField)){
            return true;
        }

        return false;
    }

    private boolean isDaggerBindInActivityOrFragment(SootField widgetField) {
        SootClass sc = widgetField.getDeclaringClass();
        if (ClassService.isActivity(sc)){
            try{
                SootMethod onCreate = sc.getMethodByNameUnsafe("onCreate");
                if (onCreate!=null && MethodService.tryRetrieveActiveBody(onCreate)){
                    Body body = onCreate.retrieveActiveBody();
                    for (Unit unit : body.getUnits()){
                        Stmt stmt = (Stmt) unit;
                        if (stmt instanceof InvokeStmt){
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            if (invokeExpr instanceof StaticInvokeExpr
                                    && invokeExpr.getMethod().getName().equals("bind")
                                    && invokeExpr.getMethod().getDeclaringClass().getName().equals("butterknife.ButterKnife")){
                                return true;
                            }
                        }
                    }
                }
            }catch (AmbiguousMethodException e){
                System.out.println("AmbiguousMethod");
            }
        }
        if (ClassService.isFragment(sc)){
            try{
                SootMethod onCreate = sc.getMethodByNameUnsafe("onCreateView");
                if (onCreate!=null && MethodService.tryRetrieveActiveBody(onCreate)){
                    Body body = onCreate.retrieveActiveBody();
                    for (Unit unit : body.getUnits()){
                        Stmt stmt = (Stmt) unit;
                        if (stmt instanceof InvokeStmt){
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            if (invokeExpr instanceof StaticInvokeExpr
                                    && invokeExpr.getMethod().getName().equals("bind")
                                    && invokeExpr.getMethod().getDeclaringClass().getName().equals("butterknife.ButterKnife")){
                                return true;
                            }
                        }
                    }
                }
            }catch (AmbiguousMethodException e){
                System.out.println("AmbiguousMethod");
            }
        }
        return false;
    }

    /**寻找控件的赋值语句，并根据赋值语句的右操作数查找控件域变量；
     * @return 查找失败则返回null*/
    private SootField findWidgetField(List<Unit> pres, Value baseObject) {
        SootField widgetField = null;
        for (int i = pres.size()-1;i>=0;i--){
            Stmt stmt1 = (Stmt) pres.get(i);
            if (stmt1 instanceof DefinitionStmt){
                Value leftOP = ((DefinitionStmt) stmt1).getLeftOp();
                //Value right = ((DefinitionStmt) stmt1).getRightOp();

                if (leftOP.equivTo(baseObject)){    // 一条赋值语句的左操作数是调用者控件
                    // 这里只考虑了右边是域的情况,基本都是域
                    // 如果用了依赖注入ViewBinding的话，右边同样是域，只是变成了对应的ViewBinding类的域；但是域名是一样的
                    if (stmt1.containsFieldRef()){
                        SootField field = stmt1.getFieldRef().getField();  // 包含field，一定是右操作数吗?
                        if (((DefinitionStmt) stmt1).getRightOp().toString().contains(field.toString())){
                            widgetField = field;
                            break;
                        }

                    }
                }
            }
        }
        return widgetField;
    }


    /** 寻找当前语句stmt的前驱中的控制依赖语句;
     * 如果找到控制依赖语句并从中提取出控制依赖d，则向上溯源d的赋值语句，将其作为sink生成sink定义
     * caller  -callsite->  sootMethod
     * @param initPath 当前stmt所在的方法
     * @param sootMethod 到达该方法的调用路径
     * @param stmt 当前语句，即寻找这个语句的前驱控制依赖
     * @param callsiteIndex 调用图边，源起点节点中，调用到该方法sootMethod的相应的语句
     * @return  成功找到控制依赖甚至是对应赋值语句，返回语句相关信息；否则没有成功找到就返回null*/
    private ControlDependency findDependency(String initPath, SootMethod sootMethod, Stmt stmt, int callsiteIndex) {
        if (!MethodService.tryRetrieveActiveBody(sootMethod))
            return null;
        Body body = sootMethod.retrieveActiveBody();
        UnitGraph unitGraph = new ExceptionalUnitGraph(body);
        MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph);  // 反支配者，其必经过的后驱
        MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱

        List<Unit> pres = dFinder.getDominators(stmt);

        for (int i = pres.size()-1;i>=0;i--){
            Stmt stmt1 = (Stmt) pres.get(i);
            if (stmt1 instanceof IfStmt){

                IfStmt ifStmt = (IfStmt) stmt1;
                Value condition = ifStmt.getCondition();
                Value conditionLocal = getConditionLocalFrom(condition,body.getLocals());

                if (conditionLocal!=null){
                    // 1.直接使用这条if语句作为sink
                    ControlDependency depend = new ControlDependency(ifStmt,conditionLocal,sootMethod);
                    return depend;

                    // 2.向上追溯该依赖变量的赋值语句。
//                    List<Unit> anotherPres = dFinder.getDominators(ifStmt);
//                    for (int j = anotherPres.size()-1;j>=0;j--){    // 这里只在本方法体内寻找, 实际上可能存在于上层调用
//                        Stmt condAssignStmt = (Stmt) anotherPres.get(j);
//                        if (condAssignStmt instanceof DefinitionStmt){
//                            Value left = ((DefinitionStmt) condAssignStmt).getLeftOp();
//                            if (left.equivTo(conditionLocal)){
//                                // 找到控制依赖变量的赋值语句了
//
//                                // 将 对该控制依赖变量的赋值语句 作为sink点定义；并将这个sink定义作为返回值
//                                    controlDependency depend = new controlDependency(condAssignStmt,left);
//                                    return depend;
//                            }
//                        }
//                    }
                }
            }
        }
        // 当前方法体内无法找到控制依赖，继续向上溯源尝试寻找。
        //if (flag != true){
        if (callsiteIndex >=0 ){
            Stmt callsite = callSitesForRestore.get(callsiteIndex);
            if (callsite !=null){ // 通常在callsiteIndex取0时，callsite取null

                String[] temp = initPath.split(" -> ");
                String methodSignature = temp[temp.length-2]; // temp.length-1就是当前方法，向上溯源因此再多-1
                SootMethod caller = Scene.v().getMethod(methodSignature);
                String subInitPath = initPath.substring(0,initPath.lastIndexOf(" -> ")); // 在当前路径上去掉当前方法
                return findDependency(subInitPath, caller, callsite, callsiteIndex-1);

            }

        }
        //}
        /* 一直向上溯源寻找都找不到，再向上已经没有的时候；
        * 例如最上层A()中也没有；则上述代码运行至 if(callsite !=null)将判断失败,则不会继续反深度优先溯源
        * 因此会退出if (flag != true)判断后运行至此
        * 返回null，表示没有成功找到并定义sink*/
        return null;
    }

    /** 以dialog的show()为启发，向上寻找所有关乎此dialog的各种属性设置API; 默认返回空List
     * @param pres dialog.show()所在stmt，在其所在方法内的所有支配者
     * @param dialogLocal 调用show()的dialog变量 */
    private List<Stmt> findDialogSettings(List<Unit> pres, Value dialogLocal) {
        Value dialog = dialogLocal;
        List<Stmt> settings = new ArrayList<>();
        for(int i = pres.size()-1;i>=0;i--){ // 逆序
            Stmt stmt = (Stmt) pres.get(i);
            if (stmt.containsInvokeExpr()){
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                Value baseObject = null;
                if (invokeExpr instanceof InstanceInvokeExpr){
                    baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                }
                if (baseObject!=null){
                    SootMethod sootMethod = invokeExpr.getMethod();
                    SootClass sootClass = sootMethod.getDeclaringClass();
                    String subSignature = sootMethod.getSubSignature();

                    if (baseObject.equivTo(dialog)
                            && ( sootMethod.isConstructor() || sootMethod.getName().equals("<init>") )
                            && ( ClassService.isAlertDialogBuilder(sootClass) || ClassService.isDialogBuilder(sootClass) )
                    ){
                        settings.add(0,stmt);
                        break;
                    }

                    if (baseObject.equivTo(dialog) &&
                            ( ClassService.isDialog(sootClass)
                                    ||ClassService.isAlertDialog(sootClass)
                                    ||ClassService.isAlertDialogBuilder(sootClass)
                                    || ClassService.isDialogBuilder(sootClass)
                                    || ClassService.isWrapperAlertDialogBuilder(sootClass))
                    ){ // dialog各种属性设置，直接存下
                        settings.add(0,stmt);
                    }
                    else {  // 调用者不是dialog; 考虑中途转为Builder类。
                        if ( ClassService.isAlertDialogBuilder(sootClass)
                                || ClassService.isDialogBuilder(sootClass) ){
                            if ( stmt instanceof DefinitionStmt ){
                                Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                                if (leftOp.equivTo(dialog)){ // 此时dialog变量的值，仍然是调用show()的变量
                                    dialog = baseObject; // 更新dialog为Builder变量
                                    settings.add(0,stmt);
                                }
                            }

                        }

                    }

                }

            }
        }

        return settings;
    }

    /**
     * 从形如 if $z0 == 0 goto xxx; 的语句中解析出控制依赖变量 $z0 .
     * 默认返回 null.
     * @param condition ifStmt.getCondition(),即 $z0 == 0
     * @param locals 当前ifStmt 所在方法的方法体所包含的所有 local变量. */
    private Value getConditionLocalFrom(Value condition, Chain<Local> locals) {
        Value value = null;
        int count = 0;
        for (ValueBox vb : condition.getUseBoxes()){
            Value v = vb.getValue();
            if (locals.contains(v)){
                count++;
                value = v;
            }
        }
        // 超过一个local时，目前则无法区分哪个是用来判断的控制依赖。保守起见，默认找不到
        // 目前来看，可采用默认顺序的第一个；但不保证完全正确
        // 因为condition.getUseBoxes()本质上是Value类的getUseBoxes()方法
        // 而Value类的getUseBoxes()遇到过无序的[或者说，不是理想中的顺序]
        if (count == 1)
            return value;
        else
            return null;
    }



    //////////////////////////////////////////////用于检测数据更改暨需要【保存】的数据的，及其对应的source点////////////////////////////////////////////////////////////

    /* 四种方式注册点击事件。（默认其余交互事件同理）
     * 1、匿名内部类。              Soot会为其生成一个类似临时类，在其中有一个方法A完成代码
     * 2、Activity类实现监听接口。   Soot会将this作为参数给ListenerSetting API，并在Activity类内有一个方法A完成代码
     * 3、布局文件绑定一个方法。      找不到ListenerSetting API，需要根据布局文件中的属性值找到相应的函数名，并在Activity类内根据名字找到方法A
     * 4、创建一个内部类实现监听接口，定义点击事件。  Soot会生成相同于1、里的类，但是不采用临时名字，而是会有同样的名字。具体同1、   */

    /**
     * 当前UI控制器的初始化的生命周期回调 initMethod 内，检测用户交互监听的注册API
     * （该注册API可能直接在 initMethod 里直接调用，也可能在其普通的调用里间接调用）;
     * 若找到，则将交互相应方法传递给handleListener()进行分析 */
    private void detectUIchangeForSave(String activityName, SootMethod initMethod, String prePath, Stmt callsite) {
        //System.out.println("现在在:"+initMethod);
        // 基于callGraph分析，得出该initMethod调用的函数列表
        List<SootMethod> callees = getCalleesFrom(initMethod);

        String initPath = prePath +" -> "+initMethod.getSignature();

        if (!MethodService.tryRetrieveActiveBody(initMethod))
            return;
        // 分析代码体
        Body body = initMethod.retrieveActiveBody();
        for (Unit unit : body.getUnits()){
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()){
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                SootMethod invokee = invokeExpr.getMethod();

                // 判断是否是监听设置API调用
                if (isListenerSetting(invokee)){    // 例外：若是在布局文件中绑定，则找不到此调用; 需要单独判断
                    String listenerPath = initPath +" : "+ stmt;
                    Value arg = invokeExpr.getArg(0);   // 1、2、4的监听代码一般是  一个实现监听接口的类里面。
                    System.out.println("\n找到一处设置用户监听API："+listenerPath);
                    System.out.println("参数是"+arg+" 类型为 "+arg.getType());

                    boolean isInterfaceType = false;
                    if (arg.getType().equals(invokee.getParameterType(0))){
                        isInterfaceType = true;
                        // 如果是接口，就再往上找一点识别一下转型cast，找到实体类
                        List<Unit> mayCast = getPrevUnits(stmt, body);
                        int limit = Math.min(mayCast.size(), 3);
                        for (int i = 1; i< limit; i++){
                            Stmt listenerCastStmt = (Stmt) mayCast.get(i);
                            if (listenerCastStmt instanceof DefinitionStmt){
                                Value left = ((DefinitionStmt) listenerCastStmt).getLeftOp();
                                Value right = ((DefinitionStmt) listenerCastStmt).getRightOp();
                                if (left.equals(arg) && right instanceof CastExpr){
                                    isInterfaceType = false;
                                    arg = ((CastExpr) right).getOp();
                                    break;
                                }
                            }
                        }

                    }
                    if (isInterfaceType)
                        System.out.println("接口类型参数，无法识别具体实现类");
                    else {
                        SootClass listenerClass = Scene.v().getSootClassUnsafe(arg.getType().toString());
                        if (listenerClass != null ){
                            //Value baseObject = ((InstanceInvokeExpr)invokeExpr).getBase();  // 监听注册必定是实例调用！一定不会错

                            String listenerMethodName = getListenerMethodName(invokee);
                            //listenerClass.getMethod();
                            //  处理存在同名函数。比如同时实现了多种点击接口，则会存在多个不同参数的onClick方法
                            SootMethod listenerMethod = getListenerImpl(listenerClass, listenerMethodName, RefType.v("android.view.View"));
                            //SootMethod listenerMethod = listenerClass.getMethodByNameUnsafe(listenerMethodName);
                            //List<Type> paramTypeList = new ArrayList<>();
                            //paramTypeList.add(RefType.v("android.view.View"));
                            //paramTypeList.add(RefType.v("android.content.DialogInterface"));
                            //paramTypeList.add(IntType.v());
                            //SootMethod listenerMethod = listenerClass.getMethodUnsafe(listenerMethodName, paramTypeList, VoidType.v());
                            if (listenerMethod!=null){
                                if (!listenerClass.getName().equals(activityName)) {    //
                                    System.out.println("找到对应的响应回调函数：" + listenerMethod.getSignature());
                                    String path = listenerPath + " -> " + listenerClass.getName();
                                    // todo 稍加处理，如果listenerMethod根据ViewId来区分不同的响应,减少重复分析
                                    handleListener(activityName, listenerMethod, path, null, new LinkedList<SootField>());    // 具体分析交互响应诸如onClick()方法里的代码
                                }
                            }else{
                                System.out.println("没有对应的响应回调函数");
                            }
                        }else{
                            System.out.println("未找到对应的类！");
                        }
                    }


                }
                if (initPath.contains(invokee.getSignature()))
                    continue;
                // 不是设置监听API，是普通调用，且包含在调用图上。则递归，继续寻找初始化的调用链上，是否还有地方在设置监听事件
                if (callees.contains(invokee)){
                    detectUIchangeForSave(activityName, invokee, initPath, stmt);    // 递归到被调用函数的函数体，执行相同检查
                }

            }   // if (stmt.containsInvokeExpr())

        }
        //System.out.println("当前方法结束，返回上一层");

    }

    private SootMethod getListenerImpl(SootClass listenerClass, String listenerMethodName, Type keyParaType) {
        for (SootMethod sm : listenerClass.getMethods()){
            if (sm.getName().equals(listenerMethodName)){
                if (sm.getParameterTypes().contains(keyParaType))
                    return sm;
            }
        }
        return null;
    }


    //List<Stmt> callSitesForSave = new ArrayList<>();

    /**
     * 传进来诸如onCLick()等 listenerMethod，分析用户交互会带来哪些数据变化（即这些数据需要“保存”的操作）*/
    private void handleListener(String activityName, SootMethod listenerMethod, String prepath, Stmt callsite, LinkedList<SootField> baseFields) {
        // path: listenerClassName -> listenerMethod()
        // father() -callsite-> listenerMethod

        // 分析监听处理代码，需要深度搜索WidgetSetting()
        // listenerMethod即为onClick()等一类具体的设置响应监听的代码体
        // 得到它调用的函数们 callee
        if (listenerMethod==null)
            return;

        List<SootMethod> callees = getCalleesFrom(listenerMethod);

        String path = prepath+" -> "+listenerMethod.getSignature();   // listenerClassName -> listenerMethod() -> A() ......
        //callSitesForSave.add(callsite); // 初始第一个元素是null，list为 [null, callsite,......]
        // 表明listenerClassName -null-> listenerMethod() -callsite->A()......

        // 遍历代码体，寻找组件的API
        if (!MethodService.tryRetrieveActiveBody(listenerMethod))
            return;
        Body body = listenerMethod.retrieveActiveBody();
        UnitGraph unitGraph = new ExceptionalUnitGraph(body);
        MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph);  // 反支配者，其必经过的后驱
        MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱


        for (Unit unit : body.getUnits()){
            handleListenerUnitDetail(activityName, path, callees, listenerMethod, dFinder, pdFinder, unit, baseFields);
        }
        //callSitesForSave.remove(callSitesForSave.size()-1);
    }

    /**对传进来的onOptionsItemSelected()方法，进行切分; 得到菜单点击item和其对应的代码片段;
     * 分析这些代码片段，分析用户交互会带来哪些数据变化(即这些虎踞需要“保存”的操作).
     * 代码片段格式为 Map[ string, list[Unit] ].
     * @param onOptionsItemSelected 传进来找到的onOptionsItemSelected()方法
     * @param initPath
     */
    private void handleMenuOptionsListener(String activityName, SootMethod onOptionsItemSelected, String initPath){
        // 本质上为handleListener()方法的特制版
        // 针对onOptionsItemSelected回调进行的第一层分析。
        // 特制版的目的是可以获得menuItem的具体值。后续若有第二,三等等层，则可以直接套用handleListener()
        if (!MethodService.tryRetrieveActiveBody(onOptionsItemSelected))
            return;
        Map<String,List<Unit>> listeners = getAndDivideMenuListenerParts(onOptionsItemSelected);    // 切分

        Body menuListenerBody = onOptionsItemSelected.retrieveActiveBody();
        UnitGraph unitGraph = new ExceptionalUnitGraph(menuListenerBody);
        MHGPostDominatorsFinder<Unit> pdFinder = new MHGPostDominatorsFinder<Unit>(unitGraph); // 反向支配者
        MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱

        List<SootMethod> callees = getCalleesFrom(onOptionsItemSelected);

        initPath = initPath + " -> " + onOptionsItemSelected.getSignature();

        //callSitesForSave.add(null);     // 表明是 activity -null-> onOptionsItemSelected()

        for (String menuItem: listeners.keySet()){
            String path = initPath+" [item case:"+menuItem+"]"; // 相比handleListener()中的实现，这边只是多了一个item case，用以显示菜单id号作区分。
            for (Unit unit : listeners.get(menuItem)){
                handleListenerUnitDetail(activityName, path, callees, onOptionsItemSelected, dFinder, pdFinder, unit, new LinkedList<SootField>());
            }
        }

        //callSitesForSave.remove(callSitesForSave.size()-1);
    }

    /** 具体处理 Listener或者是 MenuOptionsListener的每一条语句。这两个的处理相同，故单独抽出作为一个单独的方法体。后续扩充控件仅需要在此拓展即可
     * @param path 当前处理语句的路径
     * @param callees 当前处理语句所在方法的调用边
     * @param listenerMethod 当前处理语句所在方法
     * @param dFinder 当前处理语句所在方法的代码体的支配者查找器（支配：狭义上的前驱）
     * @param unit 当前处理语句   */
    private void handleListenerUnitDetail(String activityName, String path, List<SootMethod> callees, SootMethod listenerMethod,
                                          MHGDominatorsFinder<Unit> dFinder, MHGPostDominatorsFinder<Unit> pdFinder,
                                          Unit unit, LinkedList<SootField> baseFields) {
        // path:  listenerClassName -> listenerMethod()

        Stmt stmt = (Stmt) unit;
        if (stmt.containsInvokeExpr()){
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod callee = invokeExpr.getMethod();

            int kind = isWidgetSetting(callee);
            if (kind!=0){
                //System.out.println("找到控件设置API调用,界面更改: "+path+" : "+stmt);    //

                DataVariable dtB = new DataVariable();

                List<Unit> pres = null;    // 该调用语句的支配者们
                if (invokeExpr instanceof StaticInvokeExpr)
                    return;
                InstanceInvokeExpr instanceInvokeExpr = ((InstanceInvokeExpr) invokeExpr);  // 该调用语句通常是一个实际的控件对象在调用
                Value baseObject = null;   // instanceInvokeExpr中的实际控件对象

                SootField widgetField = null;
                WidgetSetting dialogSettings = null;
                Feature feature = null;

                DataVariable recoveryOne = null;
                SootField dependField = null;


                switch (kind){
                    case 1: // android.widget.TextView
                        // 1,找到这句话的调用变量，向上追踪是哪个控件，通过ID号
                        System.out.println("需要保存: find a android.widget.TextView");
                        baseObject = instanceInvokeExpr.getBase();
                        dtB.setWidgetType(baseObject.getType()); //1
                        dtB.setPath(path);  //2
                        dtB.setStmt(stmt);  //3

                        // 向上追溯调用此方法的对象的赋值,尝试找到textView的id号，用以作为特征
                        pres = dFinder.getDominators(stmt);
                        widgetField = findWidgetField(pres, baseObject);
                        if (widgetField == null){
                            System.out.println("\t"+baseObject+" 没有找到对应的");
                            break;
                        }
                        LinkedList textFather = new LinkedList<>(baseFields);
                        textFather.add(widgetField);
                        feature = new Feature(activityName, baseObject.getType(), textFather);
                        dtB.setFeature(feature);//4

                        dtB.setDependency(null);    //5

                        // 2,找到setText的参数。利用StatementSourceSinkDefinition建模为Source点
                        Value stringPara = invokeExpr.getArg(0);
                        // 2.1 这个是直接用 textView.setText(stringPara); 作为source点
//                        if (body.getLocals().contains(stringPara)){
//                            dtB.addSourceSink( addSourceForSave(stmt, (Local) stringPara) ); //6
//                        }

                        // 2.2 这个是向上寻找stringPara的赋值语句作为source、
                        for (Unit unit1 : pres ){
                            Stmt assign = (Stmt) unit1;
                            if (assign instanceof AssignStmt){
                                Value left = ((AssignStmt) assign).getLeftOp();
                                if (left.equals(stringPara)){   // left = stringPara
                                    // 碰到语句assign：   stringPara = String.valueOf(right);
                                    // 2.2.1 这个是用assign和左操作数作为source
                                    dtB.addSourceSink( addSourceForSave(assign, (Local) left ,null) );    // 6
                                    // 2.2.2 这个是用assign和右操作数，即valueOf的参数，作为source
//                                    if (body.getLocals().contains(stringPara)){
//                                        Value right = assign.getInvokeExpr().getArg(0);
//                                        dtB.addSourceSink( addSourceForSave(assign, (Local) right) );  // 6   left = String.valueOf(right)
//                                    }
                                    break;
                                }
                            }
                        }

                        // 3,划分集合元素
                        if (Smap.containsKey(feature)){
                            Smap.get(feature).add(dtB);
                        }
                        else{
                            List<DataVariable> list = new ArrayList<>();
                            list.add(dtB);
                            Smap.put(feature,list);
                        }

                        dataVariableB.add(dtB);
                        break;
                    case 2: // android.app.Dialog
                        System.out.println("需要保存: find a android.app.Dialog");
                        baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                        dtB.setWidgetType(baseObject.getType());    //1
                        dtB.setPath(path);  //2
                        dtB.setStmt(stmt);  //3

                        // 没有ID号
                        // 用各种属性设置作为特征
                        pres = dFinder.getDominators(stmt);
                        dialogSettings = new WidgetSetting(findDialogSettings(pres,baseObject));
                        feature = new Feature(activityName,baseObject.getType(),dialogSettings);
                        dtB.setFeature(feature); //4

                        dtB.setDependency(null);    //5

                        //
                        recoveryOne = findRecoveryOneSameAs(dtB);
                        if (recoveryOne == null){   // 恢复集合没有对应元素，无法索查到相关的域
                            dtB.setSourcesink(null); //6
                        }
                        else{
                            dependField = getDependField(recoveryOne);
                            if (dependField!=null){
                                List<ISourceSinkDefinition> sources = findDependencyChangeForDialog(path, dependField, dtB.getFeature().getWidgetSetting().getSettings());
                                dtB.addSourceSink(sources.get(0)); // 可能add null值
                                dtB.addSourceSink(sources.get(1)); // 可能add null值
                            }
                            else {
                                dtB.setSourcesink(null);
                            }

                        }

                        // 3,划分集合元素
                        if (Smap.containsKey(feature)){
                            Smap.get(feature).add(dtB);
                        }
                        else{
                            List<DataVariable> list = new ArrayList<>();
                            list.add(dtB);
                            Smap.put(feature,list);
                        }

                        dataVariableB.add(dtB);
                        break;
                    case 3: // androidx.drawerlayout.widget.DrawerLayout      openDrawer(android.view.View drawerView)
                        System.out.println("需要保存: find a androidx.drawerlayout.widget.DrawerLayout");
                        baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                        dtB.setWidgetType(baseObject.getType()); //1
                        dtB.setPath(path);  //2
                        dtB.setStmt(stmt);  //3

                        // 向上追溯调用此方法的对象的赋值,尝试找到id号，用以作为特征
                        pres = dFinder.getDominators(stmt);
                        widgetField = findWidgetField(pres, baseObject);
                        if (widgetField == null){
                            System.out.println("\t"+baseObject+" 没有找到对应的ID号");
                        }
                        LinkedList drawerFather = new LinkedList<SootField>(baseFields);
                        drawerFather.add(widgetField);
                        feature = new Feature(activityName, baseObject.getType(), drawerFather);
                        dtB.setFeature(feature);//4

                        dtB.setDependency(null);//5

                        // 是否是有恢复的，即映射关系存在？
                        recoveryOne = findRecoveryOneSameAs(dtB);
                        if (recoveryOne==null){
                            dtB.setSourcesink(null);//7
                        }
                        else {
                            // 先找是哪个field
                            dependField = getDependField(recoveryOne);
                            // 存在这样的field，则在当前的前后文中寻找对他的赋值更改！
                            if (dependField!=null){
                                // pres  followings
                                // 赋值可能在前可能在后; 此处寻找分析原则：先前再后
                                AssignStmt dAssign = null;
                                for (int i= pres.size()-1;i>=0;i--){
                                    Stmt assign = (Stmt) pres.get(i);
                                    if (assign instanceof AssignStmt && assign.containsFieldRef()){
                                        SootField sootField = assign.getFieldRef().getField();
                                        Value left = ((AssignStmt) assign).getLeftOp();
                                        if (sootField.equals(dependField) && left.toString().contains(sootField.toString())){    // 左操作数就是dependField
                                            dAssign = (AssignStmt)assign;
                                        }

                                    }
                                }
                                // 反向分析中没找到，则再前向分析中尝试寻找
                                if (dAssign == null){
                                    List<Unit> followings = pdFinder.getDominators(stmt);
                                    for (int i= 0;i<followings.size();i++){
                                        Stmt assign = (Stmt) followings.get(i);
                                        if (assign instanceof AssignStmt && assign.containsFieldRef()){
                                            SootField sootField = assign.getFieldRef().getField();
                                            Value left = ((AssignStmt) assign).getLeftOp();
                                            if (sootField.equals(dependField) && left.toString().contains(sootField.toString())){    // 左操作数就是dependField
                                                dAssign = (AssignStmt)assign;
                                            }
                                        }
                                    }
                                }

                                // 如果找到了这样的，对相应的某个域在左侧 的赋值语句
                                if (dAssign !=null ){
                                    // d形式: field = dRight;
                                    // 这里是向上追寻右操作数的赋值，并将其定义source
                                    Value dRight = dAssign.getRightOp();
                                    List<Unit> t = dFinder.getDominators(dAssign);
                                    boolean flag = false;
                                    for (int i = t.size()-1; i>=0;i--){
                                        Stmt stmt1 = (Stmt) t.get(i);
                                        if (stmt1 instanceof DefinitionStmt ){
                                            Value leftStmt1 = ((DefinitionStmt) stmt1).getLeftOp();
                                            if (leftStmt1.equivTo(dRight)){
                                                dtB.addSourceSink( addSourceForSave(stmt1, (Local) leftStmt1 ,null) );  //7
                                                flag = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (flag==false){
                                        // 直接将  域赋值语句作为source
                                        ISourceSinkDefinition temp = fieldAssignBeSource(dAssign);
                                        dtB.addSourceSink(temp);
                                    }

                                }else{
                                    dtB.setSourcesink(null);//7
                                }

                            }
                            else { // 受恢复的控件都没有找到控制依赖，则不存在相应的表征状态的数据，也没必要再进一步分析构建数据流source了
                                dtB.setSourcesink(null);//7
                            }

                        }

                        // 3,划分集合元素
                        if (Smap.containsKey(feature)){
                            Smap.get(feature).add(dtB);
                        }
                        else{
                            List<DataVariable> list = new ArrayList<>();
                            list.add(dtB);
                            Smap.put(feature,list);
                        }

                        dataVariableB.add(dtB);
                        break;
                    case 4: // androidx.appcompat.app.AlertDialog
                        System.out.println("需要保存: find a androidx.appcompat.app.AlertDialog");
                        baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                        dtB.setWidgetType(baseObject.getType());    //1
                        dtB.setPath(path);  //2
                        dtB.setStmt(stmt);  //3

                        pres = dFinder.getDominators(stmt);
                        dialogSettings = new WidgetSetting(findDialogSettings(pres,baseObject));
                        feature = new Feature(activityName, baseObject.getType(), dialogSettings);
                        dtB.setFeature(feature);//4

                        dtB.setDependency(null);    //5

                        //
                        recoveryOne = findRecoveryOneSameAs(dtB);
                        if (recoveryOne == null){
                            dtB.setSourcesink(null); //7
                        }
                        else{
                            dependField = getDependField(recoveryOne);
                            if (dependField!=null){
                                List<ISourceSinkDefinition> sources = findDependencyChangeForDialog(path, dependField, dtB.getFeature().getWidgetSetting().getSettings());
                                dtB.addSourceSink(sources.get(0)); // 可能是 null值
                                dtB.addSourceSink(sources.get(1)); // 可能是 null值
                            }
                            else {
                                dtB.setSourcesink(null);
                            }

                        }

                        // 3,划分集合元素
                        if (Smap.containsKey(feature)){
                            Smap.get(feature).add(dtB);
                        }
                        else{
                            List<DataVariable> list = new ArrayList<>();
                            list.add(dtB);
                            Smap.put(feature,list);
                        }

                        dataVariableB.add(dtB);
                        break;
                    default:
                        break;
                }
            }
            else{
                if (path.contains(callee.getSignature()))
                    return;
//                if (callee.equals(listenerMethod))  // 防止自己调用自己，导致无限递归栈溢出
//                    return;
                if (callees.contains(callee)||callee.getDeclaringClass().getName().contains(AppParser.v().getPkg())){
                    if (invokeExpr instanceof InstanceInvokeExpr) { // 实例(控件封装)调用，区分对象敏感性
                        Value baseObject = ((InstanceInvokeExpr) invokeExpr).getBase();
                        List<Unit> pres = dFinder.getDominators(stmt);
                        for (int i = pres.size()-1; i>=0; i--){
                            Stmt stmt1 = (Stmt) pres.get(i);
                            if (stmt1 instanceof DefinitionStmt && ((DefinitionStmt) stmt1).getLeftOp().equivTo(baseObject))
                            {
                                Value rightOp = ((DefinitionStmt) stmt1).getRightOp();
                                if (stmt1.containsFieldRef()){
                                    if ( rightOp.toString().contains( stmt1.getFieldRef().getField().toString() ) ){
                                        LinkedList list = new LinkedList<SootField>(baseFields);
                                        list.add(stmt1.getFieldRef().getField());
                                        handleListener(activityName, callee, path, stmt, list);   // 当前路径initPath下的stmt语句是一句调用，调用了方法callee;
                                        break;
                                    }
                                }
                                else if (rightOp instanceof ThisRef || rightOp instanceof ParameterRef){
                                    handleListener(activityName, callee, path, stmt, baseFields);
                                }
                            }
                        } // 找不到控件域变量的话，则放弃分析，就想分析只会导致误报
                        //detectUIRecovery(initPath, callee, stmt, true, baseFields);   // 当前路径initPath下的stmt语句是一句调用，调用了方法callee;
                    }
                    else
                        handleListener(activityName, callee, path, stmt, baseFields);
                }

            }
        }
    }
    private boolean isact(SootClass activity) {
        if (AppParser.v().getApkName().contains("atalogue") && AppParser.v().getApkName().contains("ook")){
            if (activity.getName().contains("askLis")
                    || activity.getName().contains("oodreadsExportFailures")
                    || activity.getName().contains("ookDetai")
                    || activity.getName().contains("ditAntholog")
                    || activity.getName().contains("ditField")
                    || activity.getName().contains("dministrationFunctions")
                    || activity.getName().contains("dateFromInterne")){
                return false;
            }
        }
        return true;
    }
    private void handleComplex(SootClass activity) {
        if (AppParser.v().getApkName().contains("Simpletask")){
            if (activity.getName().contains("AddTask")){
                for (SootMethod sootMethod : activity.getMethods()){
                    if (sootMethod.isStatic() && sootMethod.getName().contains("access")){
                        handleListener(activity.getName(), sootMethod, activity.getName(), null, new LinkedList<>());
                    }
                }
            }
        }
    }


    /** 从相应的恢复控件处，识别出控制依赖的域
     * @param recoveryOne 相应的恢复的控件 */
    private SootField getDependField(DataVariable recoveryOne) {
        // 首先得有控制依赖
        if (recoveryOne.getDependency() != null){
            ControlDependency acd = recoveryOne.getDependency();
            List<Unit> anotherPres = acd.getPres();
            Value cond = acd.getConditionLocal();
            // 有了控制依赖后，再判断控制依赖的变量的值，是否来自于一个field，或者理解为是一个别名
            for (int i = anotherPres.size()-1;i>=0;i--){
                Stmt assign = (Stmt) anotherPres.get(i);
                if (assign instanceof DefinitionStmt){
                    Value left = ((DefinitionStmt) assign).getLeftOp();
                    Value right = ((DefinitionStmt) assign).getRightOp();
                    if ( left.equivTo( cond ) || right.equivTo( cond )){ // 考虑到存在一种情况，条件变量是z。 z = xxx; field = z; if z goto label1; 即使用z时并没有再次从field取值
                        if (assign.containsFieldRef()){
                            SootField dependField = assign.getFieldRef().getField();
                            return dependField;
                        }
                    }
                }
            }
        }

        return null;
    }

    /** 寻找相应的对话框交互事件监听
     * @param onListener 监听回调方法的名称。例如onShow, onDismiss */
    private SootMethod findsDialogInterfaceOnListener(List<Stmt> settings, String onListener) {
        // e.g., onListener = onShow
        String name = "setOn"+onListener.substring(2)+"Listener"; // e.g.,  setOnShowListener
        String argType = "android.content.DialogInterface$On"+onListener.substring(2)+"Listener";  // e.g., android.content.DialogInterface$OnShowListener
        for (Stmt stmt : settings){
            if (stmt.containsInvokeExpr()){
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                if (sootMethod.getName().equals(name)){
                    // 是诸如setOnShowListener的设置
                    int index = -1;
                    for (Type type : sootMethod.getParameterTypes()){
                        if (type.toString().contains( argType )){ // "android.content.DialogInterface$On"
                            index = sootMethod.getParameterTypes().indexOf(type);
                            break;
                        }
                    }
                    // index记录着交互响应的参数位置
                    if (index !=-1){
                        // 根据下标找到调用表达式对应位置的实参
                        Value arg = stmt.getInvokeExpr().getArg(index);
                        // 得到实参对应的接口实现类
                        SootClass listenerClass = Scene.v().getSootClassUnsafe(arg.getType().toString());
                        if (listenerClass!=null && listenerClass.getMethodByNameUnsafe(onListener)!=null){
                            SootMethod listenerMethod = listenerClass.getMethodByNameUnsafe(onListener);
                            // 返回具体的实现方法
                            return listenerMethod;
                        }
                    }

                }
            }
        }

        return null;
    }

    /**从A集合中找出相对应的元素； 只需要比较CCDL类中的WidgetType，feature即可
     * @param b 待比较的 需要保存 的控件*/
    private DataVariable findRecoveryOneSameAs(DataVariable b) {
        for (DataVariable a : dataVariableA){
            if ( DataVariable.isSame(a,b)  ){
                return a;
            }
        }
        return null;
    }

    /** 寻找对话框的控制依赖变量域（即状态数据）， 的 赋值更改语句 。基于onshow和ondismiss监听，分别建立两个source*/
    private List<ISourceSinkDefinition> findDependencyChangeForDialog(String path, SootField field, List<Stmt> settings){
        List<ISourceSinkDefinition> sources = new ArrayList<>();

        // 1. 寻找 弹出对话框，使得状态数据更改的语句，建立source点
        SootMethod onShow = findsDialogInterfaceOnListener(settings,"onShow");
        if (onShow!=null && MethodService.tryRetrieveActiveBody(onShow)){
            Body body = onShow.retrieveActiveBody();
            UnitGraph unitGraph = new ExceptionalUnitGraph(body);
            MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱

            boolean change = false;
            for (Unit unit : body.getUnits()){ // 遍历onShow方法
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof AssignStmt && stmt.containsFieldRef()){
                    SootField sootField = stmt.getFieldRef().getField();
                    Value left = ((AssignStmt) stmt).getLeftOp();
                    if ( sootField.equals(field) && left.toString().contains(sootField.toString()) ){ // 找到控制依赖域 的赋值语句s
                        change = true;
                        boolean flag = false;
                        Value right = ((AssignStmt) stmt).getRightOp();
                        // 向上寻找一次，找到s右操作数的赋值语句。
                        List<Unit> pres = dFinder.getDominators(stmt);
                        for (int i = pres.size()-1; i>=0; i--){
                            Stmt assign = (Stmt) pres.get(i);
                            if ( assign instanceof AssignStmt && ((AssignStmt) assign).getLeftOp().equivTo(right)){
                                sources.add( addSourceForSave(assign, (Local) ((AssignStmt) assign).getLeftOp() ,null) );
                                flag = true;
                                break;
                            }
                        }
                        if (flag == true)
                            break;
                        else {
                            // 域赋值语句的右操作数，向上追寻不到赋值语句，比如直接是field = 1; 考虑直接将此句  域 的赋值语句作为source
                            // stmt
                            ISourceSinkDefinition temp = fieldAssignBeSource(stmt);
                            sources.add(temp);
                            break;
                        }
                    }
                }
            }
            if (change==false)
                sources.add(null);
        }
        else
            sources.add(null);


        // 2. 寻找 对话框关闭，使得状态数据更改的语句，建立source点
        SootMethod onDismiss = findsDialogInterfaceOnListener(settings,"onDismiss");
        if (onDismiss!=null && MethodService.tryRetrieveActiveBody(onDismiss)){
            Body body = onDismiss.retrieveActiveBody();
            UnitGraph unitGraph = new ExceptionalUnitGraph(body);
            MHGDominatorsFinder<Unit>  dFinder = new  MHGDominatorsFinder<Unit>(unitGraph); // 支配者，其必经过的前驱

            boolean change = false;
            for (Unit unit : body.getUnits()){ // 遍历onShow方法
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof AssignStmt && stmt.containsFieldRef()){
                    SootField sootField = stmt.getFieldRef().getField();
                    Value left = ((AssignStmt) stmt).getLeftOp();
                    if ( sootField.equals(field) && left.toString().contains(sootField.toString()) ){ // 控制依赖域的赋值语句s
                        change = true;
                        boolean flag = false;
                        Value right = ((AssignStmt) stmt).getRightOp();
                        // 向上寻找一次，找到s右操作数的赋值语句。
                        List<Unit> pres = dFinder.getDominators(stmt);
                        for (int i = pres.size()-1; i>=0; i--){
                            Stmt assign = (Stmt) pres.get(i);
                            if ( assign instanceof AssignStmt && ((AssignStmt) assign).getLeftOp().equivTo(right)){
                                sources.add(addSourceForSave(assign, (Local) ((AssignStmt) assign).getLeftOp() ,null) );
                                flag = true;
                                break;
                            }
                        }
                        if (flag == true)
                            break;
                        else {
                            // 域赋值语句的右操作数，向上追寻不到赋值语句，考虑直接将此句  域 赋值语句作为source
                            // stmt
                            ISourceSinkDefinition temp = fieldAssignBeSource(stmt);
                            sources.add(temp);
                            break;
                        }
                    }
                }
            }
            if (change==false)
                sources.add(null);
        }
        else
            sources.add(null);

        // 包含两个定义，分别来自于onShow和onDismiss；若其中一个没有则对应位置为null
        return sources;
    }

    /** 控制依赖变量域的赋值语句，其右操作数为常量，无法向上寻找到常量的value来源，退而求次，直接将此赋值语句定义为source，通过设定accesspath指定污染路径为base的这个域*/
    private ISourceSinkDefinition fieldAssignBeSource(Stmt stmt) {
        if (stmt instanceof AssignStmt && stmt.containsFieldRef()) {
            SootField sootField = stmt.getFieldRef().getField();
            Value left = ((AssignStmt) stmt).getLeftOp();
            if (left instanceof InstanceFieldRef){

                Set<AccessPathTuple> accessPaths = new HashSet<>();
                accessPaths.add(AccessPathTuple.fromPathElements(sootField.getName(), sootField.getType().toString(), SourceSinkType.Source));
                ISourceSinkDefinition ssdTemp =  addSourceForSave(stmt, (Local) ((InstanceFieldRef) left).getBase(), accessPaths);
                //System.out.println("Test breakpoint");
                return ssdTemp;
            }
        }
        return null;
    }


    //////////////////////////////////////////////数据流分析使用的函数////////////////////////////////////////////////////////////

    /**手动添加基于statement的source定义*/
    private ISourceSinkDefinition addSourceForSave(Stmt stmt, Local local, Set<AccessPathTuple> accessPaths) {
        if (accessPaths==null||accessPaths.isEmpty()){
            accessPaths = new HashSet<>();
            accessPaths.add(AccessPathTuple.getBlankSourceTuple()); //getBlankSourceTuple()
        }
        StatementSourceSinkDefinition source = new StatementSourceSinkDefinition(stmt, local,accessPaths); // AccessPathTuple.getBlankSourceTuple()
        saveFlowSourceSinkDefinitionProvider.addSource(source);
        return source;
    }

    /**手动添加基于statement的sink定义*/
    private ISourceSinkDefinition addSinkForRestore(Stmt stmt, Local local, Set<AccessPathTuple> accessPaths) {
        if (accessPaths==null||accessPaths.isEmpty()){
            accessPaths = new HashSet<>();
            accessPaths.add(AccessPathTuple.getBlankSinkTuple()); //getBlankSinkTuple()
        }
        StatementSourceSinkDefinition sink = new StatementSourceSinkDefinition(stmt, local, accessPaths); // AccessPathTuple.getBlankSourceTuple()
        restoreFlowSourceSinkDefinitionProvider.addSink(sink);
        return sink;
    }


    /**
     * 利用 FLowDroid 进行数据流分析。*/
    private InfoflowResults[] myRunInfoflow() throws IOException {

        setupApplication = AppParser.v().getSetupApplication(); // 全局唯一使用的FlowDroid实例。可以新的实例，然后设置使用旧的Soot实例

        // Configure more analysis options for data flow construct
        // Find the taint wrapper file
        File taintWrapperFile = new File("EasyTaintWrapperSource.txt");
        if (!taintWrapperFile.exists())
            taintWrapperFile = new File("./EasyTaintWrapperSource.txt");
        try {
            // SummaryTaintWrapper ss = new SummaryTaintWrapper(); // more powerful taint wrapper
            // 默认可使用自带的静态方法来获取一个实例：EasyTaintWrapper.getDefault();
            //setupApplication.setTaintWrapper(new EasyTaintWrapper(taintWrapperFile));   // new EasyTaintWrapper(taintWrapperFile)
            setupApplication.setTaintWrapper(new MyTaintWrapper(taintWrapperFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 自定义污染传播和反向传播处理器来单步显示传播过程
        //rtph, 正向污染传播处理器
        RecordTainPropagationHandler rtph = new RecordTainPropagationHandler();
        setupApplication.setTaintPropagationHandler(rtph);    // null by default
        //rbtph, 逆向污染传播处理器
        RecordBackwardsPropagationHandler rbtph = new RecordBackwardsPropagationHandler();
        setupApplication.setBackwardsPropagationHandler(rbtph);  // null by default

        // this option means using existing soot instance and call graph
        setupApplication.getConfig().setSootIntegrationMode(InfoflowAndroidConfiguration.SootIntegrationMode.UseExistingCallgraph);
        //setupApplication.getConfig().setCallgraphAlgorithm(InfoflowConfiguration.CallgraphAlgorithm.AutomaticSelection);  // no need
        //setupApplication.getConfig().setOneComponentAtATime(true); // false by default
        //setupApplication.getConfig().getAnalysisFileConfig().setOutputFile("");
        //setupApplication.getConfig().getCallbackConfig().setEnableCallbacks();    // true by default
        //setupApplication.getConfig().setMergeDexFiles(true); // no need again
        setupApplication.getConfig().setImplicitFlowMode(InfoflowConfiguration.ImplicitFlowMode.NoImplicitFlows);   // by default
        setupApplication.getConfig().setStopAfterFirstFlow(false);
        setupApplication.getConfig().setCodeEliminationMode(InfoflowConfiguration.CodeEliminationMode.PropagateConstants);   // "PropagateConstants" by default; RemoveSideEffectFreeCode
        setupApplication.getConfig().setAliasingAlgorithm(InfoflowConfiguration.AliasingAlgorithm.FlowSensitive);   // by default
        setupApplication.getConfig().setEnableArrayTracking(true);
        setupApplication.getConfig().setEnableExceptionTracking(false); // true by default
        //setupApplication.getConfig().setEnableStaticFieldTracking(false); //  FlowDroid2.9 没有setEnableStaticFieldTracking()函数了,下一句应该是替代
        //setupApplication.getConfig().setStaticFieldTrackingMode(InfoflowConfiguration.StaticFieldTrackingMode.None);  // StaticFieldTrackingMode.ContextFlowSensitive by default
        setupApplication.getConfig().getAnalysisFileConfig().setAdditionalClasspath("./resource/android-support-v4.jar");

        // two following for taint propagation path constructing
        //setupApplication.getConfig().getPathConfiguration().setPathBuildingAlgorithm();   // ContextSensitive by default
        setupApplication.getConfig().getPathConfiguration().setPathReconstructionMode(InfoflowConfiguration.PathReconstructionMode.Precise);    // NoPaths by default. 设置污染路径输出模式


        //setupApplication.getConfig().setLogSourcesAndSinks(true);

        setupApplication.getConfig().setExcludeSootLibraryClasses(false);    // false by default
        //setupApplication.getConfig().setIgnoreFlowsInSystemPackages(true);  // false by default

        setupApplication.getConfig().setDataFlowTimeout(300);//300
        setupApplication.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(300);//300
        if (AppParser.v().getApkName().contains("K-9")){
            setupApplication.getConfig().setDataFlowTimeout(150);//300
            setupApplication.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(150);
        }

        // 对于保存操作进行数据流分析
        showSourceSinkDefinition("\n初始化后的Save Provider：", saveFlowSourceSinkDefinitionProvider);
        System.out.println("正在运行保存数据流检测：");
        InfoflowResults saveResult = setupApplication.runInfoflow(saveFlowSourceSinkDefinitionProvider);
        handleInfoflowResult(saveResult);
        //rtph.showForward();

        // 对于恢复操作进行数据流分析
        restoreFlowSourceSinkDefinitionProvider.handleSpecialAPIsFromXML(setupApplication.getConfig());
        showSourceSinkDefinition("\n初始化后的Restore Provider：", restoreFlowSourceSinkDefinitionProvider);
        System.out.println("正在运行恢复数据流检测：");
        InfoflowResults restoreResult = setupApplication.runInfoflow(restoreFlowSourceSinkDefinitionProvider);
        handleInfoflowResult(restoreResult);


        InfoflowResults[] results = new InfoflowResults[2];
        results[0] = saveResult;
        results[1] = restoreResult;
        return results;

    }

    /** 处理数据流分析结果 */
    private void handleInfoflowResult(InfoflowResults result) {
        //displayResult(result);
        if (result.getResults() != null) {
            for (ResultSinkInfo sink : result.getResults().keySet()) {
                logger.info("Found a flow to sink {}, from the following sources:", sink);
                sink.getDefinition();
                for (ResultSourceInfo source : result.getResults().get(sink)) {
                    logger.info("\t- {}", source.getStmt());
                    if (source.getPath() != null)
                        logger.info("\t\ton Path {}", Arrays.toString(source.getPath()));
                }
            }
        }
    }

    /**展示打印数据流分析结果*/
    private void displayResult(InfoflowResults result) {
        //saveResult.printResults();
        if (result.getResults() != null) {
            for (ResultSinkInfo sink : result.getResults().keySet()) {
                logger.info("Found a flow to sink {}, from the following sources:", sink);
                for (ResultSourceInfo source : result.getResults().get(sink)) {
                    logger.info("\t- {}", source.getStmt());
                    if (source.getPath() != null)
                        logger.info("\t\ton Path {}", Arrays.toString(source.getPath()));
                }
            }
        }
    }



    //////////////////////////////////////////////辅助分析使用的功能函数/////////////////////////////////////////////////////////////

    /** 展示报告数据丢失错误 */
    private void displayDL(){
        ;
    }

    /** 得到以sootMethod为起点的可达调用方法，且它们是APP的类的方法而不是android的方法*/
    private List<SootMethod> getCalleesFrom(SootMethod sootMethod) {

        Iterator<Edge> outOf = callGraph.edgesOutOf(sootMethod);
        List<SootMethod> callees = new ArrayList<>();
        while(outOf.hasNext()){
            Edge edge = outOf.next();
            SootMethod sm = edge.getTgt().method();
//            if (sm.equals(sootMethod))
//                System.out.println();
            if (!sm.isConstructor() && AppParser.v().isAppClass(sm.getDeclaringClass().getName()) && !sm.equals(sootMethod)){ // 构造函数考虑在内将导致栈溢出，因为会涉及到很多非app本身而是java库里的构造函数
                callees.add(sm);
            }
        }
        return callees;
    }

    /**
     * 成功返回Map[ String,List[Unit] ]; 失败返回 空 Map。String是menuItem号，List是该号对应的交互响应代码
     */
    private Map<String,List<Unit>> getAndDivideMenuListenerParts(SootMethod onOptionsItemSelectedMethod) {
        //Map< String, List<Unit> > ; // String is menuItemID, List<Unit> is codes set.

        Map<String,List<Unit>> Targets = new HashMap<>();   // String is menuItemID, List<Unit> is codes set.
        List<String> menuItemIds;   // 所有menuItem的列表
        List<Unit> ts;  // 每个menuItem对应的跳转代码的第一句的集合。

        if (!MethodService.tryRetrieveActiveBody(onOptionsItemSelectedMethod))
            return Targets;

        Body body = onOptionsItemSelectedMethod.retrieveActiveBody();
        UnitGraph unitGraph = new BriefUnitGraph(body);
        MHGPostDominatorsFinder<Unit> pdf = new MHGPostDominatorsFinder(unitGraph);
        // 找出方法参数列表中的MenuItem参数
        int paraCount = onOptionsItemSelectedMethod.getParameterCount();
        int paraIndex = -1;
        for (int i =0; i<paraCount; i++){
            Type paraType = onOptionsItemSelectedMethod.getParameterType(i);
            if (paraType.toString().equals("android.view.MenuItem")){
                paraIndex = i;
                break;
            }

        }
        if (paraIndex != -1){ // 不是 -1, 证明找到了MenuItem参数，下标为paraIndex
            Local paraMenuItem = body.getParameterLocal(paraIndex);
            Value condition = null; // 会经过getItemId()转化得到真正用于判断跳转的local; 称其为condition
            Iterator var2;

            // 寻找getItemId()调用语句，其左操作符为condition
            var2 = body.getUnits().iterator();
            while (var2.hasNext()){
                Unit unit = (Unit)var2.next();
                Stmt stmt = (Stmt) unit;

                if (stmt instanceof AssignStmt){ // $i0 = interfaceinvoke $r1.<android.view.MenuItem: int getItemId()>();
                    AssignStmt aStmt = (AssignStmt) stmt;
                    if ( aStmt.getLeftOp().getType() instanceof IntType && aStmt.getRightOp() instanceof InvokeExpr){
                        InvokeExpr invokeExpr = (InvokeExpr) aStmt.getRightOp();
                        if ( invokeExpr.getMethod().getSignature().equals("<android.view.MenuItem: int getItemId()>") ){
                            if (valueACanContainValueB(aStmt.getRightOp(),paraMenuItem)){
                                condition = aStmt.getLeftOp();
                                break;
                            }
                        }
                    }
                }
            }

            // 成功找到了condition, 则可以进行后续的交互响应代码的切分. 完成对menuItemIds, ts 两个变量的赋值.  并最终添加元素到Map Targets中返回
            if (condition!=null){

                boolean switchFlag = false;

                // 先判断是不是用的switch语句。如果是，则处理完直接返回
                var2 = body.getUnits().iterator();
                while (var2.hasNext()){
                    Unit unit = (Unit)var2.next();
                    Stmt stmt = (Stmt) unit;

                    if (stmt instanceof SwitchStmt) {
                        SwitchStmt sStmt = (SwitchStmt) stmt;
                        if ( sStmt.getKey().equals(condition)) {

                            switchFlag = true;

                            menuItemIds = getMenuItemIdsFromSwitch(sStmt);
                            ts = sStmt.getTargets();

                            for (int i = 0; i < menuItemIds.size(); i++) {
                                List<Unit> target = new ArrayList<>();
                                if (i < ts.size()) {
                                    target.addAll(getFollowingUnits(ts.get(i), body));   //  ts.get(i)为当前menuItem对应的target首句代码
                                    Targets.put(menuItemIds.get(i), target);    // 这里有对应的menuItemId和对应部分代码的
                                }
                            }
                            break;
                        }
                    }

                }
//                if (switchFlag == true){
//                    return Targets;
//                }

                // 到这里说明用的不是switch; 而是用的一句一句if进行跳转(常见于跳转较少的情况, Soot将把单句switch转为一系列if)
                var2 = body.getUnits().iterator();
                menuItemIds = new ArrayList<>();
                ts = new ArrayList<>();
                while (var2.hasNext()){
                    Unit unit = (Unit)var2.next();
                    Stmt stmt = (Stmt) unit;

                    if (stmt instanceof IfStmt){
                        IfStmt ifStmt = (IfStmt) stmt;
                        if ( valueACanContainValueB(ifStmt.getCondition(),condition) ){ // 检查if语句的条件中，是否使用的是  从menuItem中得到的资源id号的Value
                            // true, 则为一种对应的menuItem事件响应
                            menuItemIds.add(getMenuItemIdFromIf(ifStmt));
                            if (ifStmt.toString().contains("!="))
                                ts.add((Unit) var2.next());
                            else
                                ts.add(ifStmt.getTarget());

                        }
                    }
                }
                for (int i = 0; i < menuItemIds.size(); i++) {
                    List<Unit> target = new ArrayList<>();
                    target.addAll(getFollowingUnits(ts.get(i),body));   //  ts.get(i)为当前menuItem对应的target首句代码
                    Targets.put(menuItemIds.get(i), target);    // 这里有对应的menuItemId和对应部分代码的
                }
                return Targets;

            }

        }

        return Targets;
    }


    /**
     * 检查A的使用的Value集合中是否包含B */
    private boolean valueACanContainValueB(Value A, Value B) {
        for (ValueBox vb : A.getUseBoxes()){
            if (vb.getValue().equals(B)){
                return true;
            }
        }
        return false;
    }

    /**
     * 根据传进来的Switch语句，转为string进行分析，继而得到每个case后面跟的menuItem; 失败返回空List*/
    private List<String> getMenuItemIdsFromSwitch(Stmt stmt) {
        String stmtStr = stmt.toString();
        List<String> MenuItemIds = new ArrayList<>();
        int p = stmtStr.indexOf("case",0);
        while(p!=-1 && p<stmtStr.length()){
            int i,j;

            i = p+4;
            while(i<stmtStr.length()){
                if (stmtStr.charAt(i) == ' '){
                    i++;
                }else
                    break;
            }
            j = i;
            while( j<stmtStr.length() ){
                if (isNumChar(stmtStr.charAt(j))){
                    j++;
                }else
                    break;
            }
            if (i<j){
                String menuItemId = stmtStr.substring(i,j);
                MenuItemIds.add(menuItemId);
            }

            p = stmtStr.indexOf("case",j);  // p = -1, if there is no such occurrence.
        }

        return MenuItemIds;
    }

    /**
     * 根据传进来的If语句，转为string进行分析，继而得到条件中的menuItem; 失败返回null*/
    private String getMenuItemIdFromIf(Stmt stmt){
        String MenuItemId = null;

        IfStmt ifs = (IfStmt) stmt;
        String condition = ifs.getCondition().toString();   // e.g.  $i0 == 16908332
        int i = condition.indexOf("= ");
        if (i!=-1){
            while(i<condition.length()){
                if (!isNumChar(condition.charAt(i)) ){
                    i++;
                }else
                    break;
            }
            int j = i;
            while( j<condition.length() ){
                if (isNumChar(condition.charAt(j))){
                    j++;
                }else
                    break;
            }
            if (i<j){
                MenuItemId = condition.substring(i,j);
            }
        }

        return MenuItemId;
    }

    /**
     * 判断字符c是否是数字*/
    private boolean isNumChar(char c) {
        String num = "1234567890";
        if (num.contains(String.valueOf(c)))
            return true;
        return false;
    }

    /**
     * 返回包含unit1在内的前面的语句，逆序*/
    private List<Unit> getPrevUnits(Unit unit1, Body body) {
        List<Unit> units = new ArrayList<>();
        for (Unit unit : body.getUnits()) {
            units.add(unit);
            if (unit.equals(unit1)) {
                break;
            }
        }
        List<Unit> list = new ArrayList<>();
        for (int i = units.size() - 1; i >= 0; i--) {    // 逆序返回
            list.add(units.get(i));
        }
        return list;
    }

    /**
     * 返回包含unit1在内的后续语句 */
    private List<Unit> getFollowingUnits(Unit unit1, Body body) {
        List<Unit> units = new ArrayList<>();
        boolean flag = false;
        for (Unit unit : body.getUnits()) {
            if (unit.equals(unit1)) {
                flag = true;
                units.add(unit);
            }
            else if(flag){
                units.add(unit);
                if (unit instanceof ReturnStmt){
                    flag = false;
                    break;
                }
            }
        }
        return units;
    }

    /**
     * 检查方法invokee 是否是用户将傲虎监听注册API？ e.g. textView.setOnCLickListener()*/
    private boolean isListenerSetting(SootMethod invokee) {
        String invokeeName = invokee.getName();
        if (SetOnUserListener.getListener().contains(invokeeName))
            return true;
        return false;
    }

    /**
     * 根据交互响应注册的API解析出响应的方法名。e.g. setOnClickListener()可以解析出 onClick()*/
    private String getListenerMethodName(SootMethod invokee) {
        String invokeeName = invokee.getName(); // setOnXXXListener
        int l = invokeeName.indexOf("On");
        int r = invokeeName.indexOf("Listener");
//        System.out.println("setting API 是：  "+invokeeName);
//        System.out.println(l);
//        System.out.println(r);
        String subListener = invokeeName.substring(l+2,r);
        subListener = "on"+subListener;
        //System.out.println(subListener);

        return subListener;
    }

    /**
     * 判断传进来的API方法是不是控件设置相关API, e.g., textView.setText(),dialog.show()  并返回相应的类型。若不是，则返回 0;*/
    private int isWidgetSetting(SootMethod invokee) {
        SootClass declaringClass = invokee.getDeclaringClass();
        String invokeeSubSignature = invokee.getSubSignature();
        String invokeeName = invokee.getName();

        if (ClassService.isTextView(declaringClass) && !ClassService.isEditText(declaringClass)
                && invokeeSubSignature.equals("void setText(java.lang.CharSequence)")){    //void setText(java.lang.CharSequence)
            return 1;
        }
        // 可以添加更多类型
        if ((ClassService.isDialog(declaringClass) || ClassService.isDialogBuilder(declaringClass))
                && invokeeName.equals("show")){ //virtualinvoke $r5.<android.app.Dialog: void show()>();
            return 2;
        }
        if (ClassService.isDrawerLayout(declaringClass)
                && invokeeSubSignature.equals("void openDrawer(android.view.View drawerView)")){
            //androidx.drawerlayout.widget.DrawerLayout public void openDrawer(@NonNull android.view.View drawerView)
            return 3;
        }
        if ((ClassService.isAlertDialog2(declaringClass)
                || ClassService.isAlertDialogBuilder(declaringClass)
                || ClassService.isWrapperAlertDialogBuilder(declaringClass))
                && invokeeName.equals("show")){
            //androidx.appcompat.app.AlertDialog: void show()
            return 4;
        }
        if (ClassService.isEditText(declaringClass)
                && invokeeSubSignature.equals("void setText(java.lang.CharSequence)")){    //void setText(java.lang.CharSequence)
            return 5;
        }
//        if (( ClassService.isWrapperAlertDialogBuilder(declaringClass) )
//                && invokeeName.equals("show")){    //void setText(java.lang.CharSequence)
//            return 6;
//        }
        return 0;
    }


    private void showSourceSinkDefinition(String s, ISourceSinkDefinitionProvider provider) {
        System.out.println(s);
        System.out.println("Sources：");
        System.out.println(provider.getSources());
        System.out.println("Sinks：");
        System.out.println(provider.getSinks());
    }

    /** 用来测试不同的source、sink定义“漂移” */
    private void TestForSourceSinkDefinition(){
        // 计数器Counter类的域设置为source、sink定义
//        for (SootClass sootClass : AppParser.v().getAllClasses()){
//            if (sootClass.getName().contains("Counter")){
//                SootField f = sootClass.getFieldByNameUnsafe("number");
//                if (f!=null){
//                    System.out.println(f.getSignature());
//                    FieldSourceSinkDefinition source1 = new FieldSourceSinkDefinition(f.getSignature());
//                    saveFlowSourceSinkDefinitionProvider.addSource(source1);
//                }
//
//            }
//        }



        // 补充ViewModel
        for(SootClass sc : AppParser.v().getViewmodels()){
            for(SootMethod sm : sc.getMethods()){
                if (!MethodService.tryRetrieveActiveBody(sm))
                    continue;
                Body body = sm.retrieveActiveBody();
                for(Unit unit : body.getUnits()){
                    Stmt stmt = (Stmt) unit;
                    if (stmt.containsInvokeExpr()){
                        SootMethod sootMethod = stmt.getInvokeExpr().getMethod();

                        // 恢复操作的source
                        if (sootMethod.getSignature().equals("<android.content.SharedPreferences: int getInt(java.lang.String,int)>")){
                            System.out.println(stmt);
                            if (stmt instanceof AssignStmt){
                                Value value =  ((AssignStmt) stmt).getLeftOp();
                                Set<AccessPathTuple> accessPaths = new HashSet<>();
                                accessPaths.add(AccessPathTuple.getBlankSourceTuple()); //getBlankSourceTuple()
                                StatementSourceSinkDefinition source = new StatementSourceSinkDefinition(stmt,(Local) value,accessPaths);
                                restoreFlowSourceSinkDefinitionProvider.addSource(source);
                            }
                        }

                        // 保存操作的sink
                        if (sootMethod.getSignature().equals("<android.content.SharedPreferences$Editor: android.content.SharedPreferences$Editor putInt(java.lang.String,int)>")){
                            System.out.println(stmt);
                            Value value = stmt.getInvokeExpr().getArg(1);
                            Set<AccessPathTuple> accessPaths = new HashSet<>();
                            accessPaths.add(AccessPathTuple.getBlankSinkTuple());
                            StatementSourceSinkDefinition sink = new StatementSourceSinkDefinition(stmt,(Local) value,accessPaths);
                            saveFlowSourceSinkDefinitionProvider.addSink(sink);
                        }

                    }
                }
            }
        }

        // 其他补充测试

    }


}
