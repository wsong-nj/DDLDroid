package tool.basicAnalysis;

import brut.androlib.ApkDecoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.axml.AXmlDocument;
import soot.jimple.infoflow.android.axml.AXmlHandler;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.axml.ApkHandler;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;
import tool.componentStructure.Activity;
import tool.componentStructure.ActivityLayoutComponentTree;
import tool.componentStructure.IntentFilter;
import tool.staticGuiModel.Widget;
import tool.utils.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用（APK）解析器，获取一些分析需要的资源
 */
public class AppParser {
    private static final String TAG = "[AppParser]";

    private String apkDir;    // "D:\\AppsForFDLDroidTest\\APK\\"

    public String getApkName() {
        return apkName;
    }

    private String apkName;    // "app-release"
    private String apkFile;    // apkFile = apkDir + apkName + ".apk"
    private String androidJAR;  // The path to the Android SDK's "platforms" directory for soot selecting android.jar automatically

    private SetupApplication setupApplication;  // FlowDroid

    private String pkg;
    private String launchActivityName;

    private List<String> activityNames = new ArrayList<String>();
    private List<Activity> implicitActivities = new ArrayList<Activity>();

    private ApkHandler apkHandler;

    private CallGraph cg;

    private static AppParser instance = null;

    public static void reset() {
        instance = null;
        ResConvertor.reset();
        IOService.reset();
    }

    public static AppParser v() {
        if (instance == null) {
            synchronized (AppParser.class) {
                if (instance == null) {
                    instance = new AppParser();
                }
            }
        }
        return instance;
    }

    private AppParser() {
    }

    public CallGraph getCg() {
        return cg;
    }
    public SetupApplication getSetupApplication() {
        return setupApplication;
    }

    /* All classes of application. Exclude classes of library as much as possible. */
    private List<SootClass> allClasses = new ArrayList<>();
    /* R$id, R$string, R$menu, R$layout, R$xml, R$navigation */
    private List<SootClass> rClasses = new ArrayList<>();
    /* Different kinds of allClasses */
    private List<SootClass> activities = new ArrayList<SootClass>();
    private List<SootClass> fragments = new ArrayList<SootClass>();
    private List<SootClass> asynctasks = new ArrayList<SootClass>();
    private List<SootClass> listeners = new ArrayList<SootClass>();
    private List<SootClass> adapters = new ArrayList<SootClass>();
    private List<SootClass> ButterKnife_viewBindings = new ArrayList<SootClass>();
    private List<SootClass> viewmodels = new ArrayList<SootClass>();
    private List<ActivityLayoutComponentTree> activityComponentTrees = new ArrayList<ActivityLayoutComponentTree>();

    public List<SootClass> getAllClasses() {
        return allClasses;
    }
    public List<SootClass> getActivities() {
        return activities;
    }
    public List<SootClass> getFragments() {
        return fragments;
    }
    public List<SootClass> getAsynctasks() {
        return asynctasks;
    }
    public List<SootClass> getListeners() {
        return listeners;
    }
    public List<SootClass> getAdapters() {
        return adapters;
    }
    public List<SootClass> getButterKnife_viewBindings() {
        return ButterKnife_viewBindings;
    }
    public List<SootClass> getViewmodels() {
        return viewmodels;
    }
    public List<ActivityLayoutComponentTree> getActivityComponentTrees() {
        return activityComponentTrees;
    }

    public String getPkg() {
        return pkg;
    }

    public ApkHandler getApkHandler() {
        return apkHandler;
    }

    //////////////////////////////////////////////////
    //        Initialize, load analysis scene       //
    //////////////////////////////////////////////////
    public void init(String apkDir, String apkName, String androidJAR) {
        this.apkDir = apkDir;
        this.apkName = apkName;
        this.apkFile = apkDir + apkName + ".apk";
        this.androidJAR = androidJAR;

        //反编译APK
        decompileApk();
        //解析Manifest文件，获取包名、Activity的名字、IntentFilter等信息
        readManifest();

        //初始化FlowDroid ( 包括Soot )
        initFlowDroid();
        buildCG(); // After this, Soot will be initialized by FlowDroid


        //处理Soot提供的所有类，根据包名排除掉不属于应用的类，以减少分析（有时候包名不代表应用的代码的包名，特殊处理）
        resolveAllClasses();
        //将筛选过后的属于应用的类，进行分类（Activity、Fragment、资源类(R$id, R$sting等)）等
        collectElement();

        //初始化输出模块，写入包名（用于行为分析器模块）
        IOService.v().init(apkDir, apkName);
        IOService.v().writePkg(pkg);

        //解析资源类(public.xml或R$id, R$sting等)，获取资源信息，输出
        ResConvertor.v().parse(apkDir, apkName, rClasses); // 其中调用ResConvertor的write() 里再调用 IOService.v().writeResources() 和 IOService.v().writeString()

        //构建界面组件树
        buildLayoutInformation();

    }

    /**反编译apk文件*/
    private void decompileApk() {
        Logger.i(TAG, "============== Decompile Apk ==============");
        File apk = new File(apkFile);
        ApkDecoder apkDecoder = new ApkDecoder();
        try {
            File decompileFile = new File(apkDir + apkName, "decompile");
            apkDecoder.setOutDir(decompileFile);
            apkDecoder.setForceDelete(true);
            apkDecoder.setApkFile(apk);
            apkDecoder.setDecodeSources((short) 0);
            apkDecoder.decode();
            Logger.i(TAG, "Decompile Apk successful!");
        } catch (Exception e) {
            Logger.e(TAG, new DecompileException("Decompile Apk fail!", e));
        }

    }

    /**读Manifest文件，识别出所有的activity名称，以及其中的launch activity*/
    private void readManifest() {
        Logger.i(TAG, "============== Read Manifest ==============");
        File apk = new File(apkFile);
        try (ProcessManifest processManifest = new ProcessManifest(apk)) {
            pkg = processManifest.getPackageName();
            if (apkName.contains("Simpletask"))
                pkg = pkg.substring(0,24);
            ExcludePackage.v().setPkgName(pkg);
            Logger.i(TAG, "Package name: " + pkg);
            //collect activities
            List<AXmlNode> activityNodes = processManifest.getAllActivities();

            for (AXmlNode actNode : activityNodes) {
                String actName = actNode.getAttribute("name").getValue().toString();
//				Logger.i(TAG, actName);
                if (actName.startsWith(".")) {
                    actName = pkg + actName;
                }
                Logger.i(TAG, "Find an Activity: " + actName);
                activityNames.add(actName);
//                //collect the activities which can be started implicitly
//                ArrayList<IntentFilter> intentFilters = new ArrayList<IntentFilter>();
//                List<AXmlNode> intentFilterNodes = actNode.getChildrenWithTag("intent-filter");
//                for (AXmlNode intentFilterNode : intentFilterNodes) {
//                    boolean isImplicit = false;
//                    //categories
//                    List<AXmlNode> categoryNodes = intentFilterNode.getChildrenWithTag("category");
//                    ArrayList<String> categories = new ArrayList<String>();
//                    for (AXmlNode categoryNode : categoryNodes) {
//                        String category = categoryNode.getAttribute("name").getValue().toString();
//                        if (category.equals("android.intent.category.DEFAULT")) {
//                            isImplicit = true;
//                        } else
//                            categories.add(category);
//                    }
//                    if (!isImplicit) {
//                        continue;
//                    }
//                    //actions
//                    List<AXmlNode> actionNodes = intentFilterNode.getChildrenWithTag("action");
//                    ArrayList<String> actions = new ArrayList<String>();
//                    for (AXmlNode actionNode : actionNodes) {
//                        String action = actionNode.getAttribute("name").getValue().toString();
//                        actions.add(action);
//                    }
//
//                    IntentFilter intentFilter = new IntentFilter(actions, categories);
//
//                    //data types
//                    List<AXmlNode> dataNodes = intentFilterNode.getChildrenWithTag("data");
//                    for (AXmlNode dataNode : dataNodes) {
//                        if (dataNode.hasAttribute("mimeType")) {
//                            String dataType = dataNode.getAttribute("mimeType").getValue().toString();
//                            intentFilter.addDataType(dataType);
//                        }
//                    }
//                    intentFilters.add(intentFilter);
//                }
//                if (!intentFilters.isEmpty()) {
//                    Activity implicitActivity = new Activity(actName, intentFilters);
//                    implicitActivities.add(implicitActivity);
//                }

            }
            //launch activity
            Logger.i(TAG, "-------------- --------------");
            Set<AXmlNode> launchActivities = processManifest.getLaunchableActivityNodes();
            for (AXmlNode la : launchActivities) {
                launchActivityName = la.getAttribute("name").getValue().toString();
                Logger.i(TAG, "Launch Activity: " + launchActivityName);
                if (ProcessManifest.isAliasActivity(la)) {
                    AXmlNode lac = processManifest.getAliasActivityTarget(la);
                    launchActivityName = lac.getAttribute("name").getValue().toString();
                }
                Logger.i(TAG, "Real Launch Activity: " + launchActivityName);
                break;
            }
            apkHandler = processManifest.getApk();
        } catch (Exception e) {
            Logger.e(TAG, new AppParseException("Read manifest file exception", e));
        }
    }


    /**初始化FlowDroid，包括一些soot的设置*/
    private void initFlowDroid() {
        setupApplication = new SetupApplication(androidJAR, apkFile);   // "D:\\Android\\AndroidSDK\\platforms"
        SootConfigForAndroid sootConfig = new SootConfigForAndroid() {
            @Override
            public void setSootOptions(Options options, InfoflowConfiguration config) {
                // excludeList 内容更多，其余同SootConfigForAndroid
                List<String> excludeList = new LinkedList<String>();
                excludeList.addAll(ExcludePackage.v().excludes);
                for (String s : excludeList){
                    if (pkg.startsWith(s.substring(0,s.indexOf("*")))){
                        excludeList.remove(s);
                        break;
                    }
                }
                options.set_exclude(excludeList);
                Options.v().set_no_bodies_for_excluded(true);

                //Scene.v().addBasicClass("",SootClass.BODIES);

            }
        };
        setupApplication.setSootConfig(sootConfig);
        setupApplication.setCallbackFile("./AndroidCallbacks.txt"); // tha same with the one in soot-infoflow-android.jar; we do so for the purpose to improve it in future

        // the following 4 configurations is corresponding with Soot init
        setupApplication.getConfig().setEnableLineNumbers(true);    // false by default
        setupApplication.getConfig().setMergeDexFiles(true);
        //setupApplication.getConfig().setEnableReflection(false); // false bt default; this can lead to more edges
        //setupApplication.getConfig().setEnableOriginalNames(true);    // false by default
        //setupApplication.getConfig().setWriteOutputFiles(false);   // false by default; do not use true otherwise analysis fail because Soot's Jimple output

        // other Data Flow analysis configuration done before runInfoflow();
        // Here configuration for constructCallGraph() is enough.

    }

    /**调用FlowDroid的调用图构造方法，并进行一定的加工*/
    private void buildCG() {
        Logger.i(TAG, "Start to initialize Soot and construct CallGraph with FlowDroid...");
        long startTime = System.currentTimeMillis();
        //setupApplication.getConfig().setCallgraphAlgorithm(InfoflowConfiguration.CallgraphAlgorithm.RTA);
        setupApplication.getConfig().setDataFlowTimeout(300);//300
        setupApplication.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(300);
        if (AppParser.v().getApkName().contains("K-9")){
            setupApplication.getConfig().setDataFlowTimeout(150);//300
            setupApplication.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(150);
        }
        setupApplication.constructCallgraph();  // here, FlowDroid will run its initializeSoot()
        long endTime = System.currentTimeMillis();
        long constractCgTime = endTime - startTime;
        Logger.i(TAG, "End, took "+constractCgTime+"ms");//87755ms//241490ms//48472ms

        cg = Scene.v().getCallGraph();
        Logger.i(TAG, "Callgraph has "+cg.size()+" edges");
        //System.out.println(cg);

        //handle callgraph
        //remove edges
        /*int removeSize = 0;
        Iterator<soot.jimple.toolkits.callgraph.Edge> cgIter = cg.iterator();
        while(cgIter.hasNext()) {
            soot.jimple.toolkits.callgraph.Edge e = cgIter.next();
            SootMethod src = e.src();
            SootMethod tgt = e.tgt();
            if (src!=null && tgt!=null)
                if(!src.getDeclaringClass().getName().equals("dummyMainClass")
                        && !tgt.getDeclaringClass().getName().equals("dummyMainClass")) {
                    if(!isAppClass(src.getDeclaringClass().getName())
                            || !isAppClass(tgt.getDeclaringClass().getName())) {
                        cgIter.remove();
                        removeSize++;
                    }
                }

        }
        Logger.i(TAG, "Remove "+removeSize+" edges");
        */

        //add
        int addSize = 0;
        for(SootClass sc : allClasses) {
            for(SootMethod sm : sc.getMethods()) {
                try {
                    Body body = sm.retrieveActiveBody();
                    Set<String> targetMethodSig = new HashSet<String>();
                    Iterator<soot.jimple.toolkits.callgraph.Edge> iter = cg.edgesOutOf(sm);
                    while(iter.hasNext()) {
                        soot.jimple.toolkits.callgraph.Edge e = iter.next();
                        SootMethod target = e.tgt();
                        System.out.println(target.getSignature());
                        if(isAppClass(target.getDeclaringClass().getName())) {
                            targetMethodSig.add(target.getSignature());
                        }
                    }
                    for(Unit unit : body.getUnits()) {
                        Stmt stmt = (Stmt)unit;
                        if(stmt.containsInvokeExpr() || stmt instanceof InvokeStmt) {
                            SootMethod invokee = stmt.getInvokeExpr().getMethod();
                            if(isAppClass(invokee.getDeclaringClass().getName())) {
                                if(!targetMethodSig.contains(invokee.getSignature())) {
                                    try {
                                        soot.jimple.toolkits.callgraph.Edge newEdge = new soot.jimple.toolkits.callgraph.Edge(sm, stmt, invokee);
                                        cg.addEdge(newEdge);
                                        addSize++;
                                    } catch (RuntimeException e) {
                                        soot.jimple.toolkits.callgraph.Edge newEdge = new soot.jimple.toolkits.callgraph.Edge(sm, unit, invokee, Kind.INVALID);
                                        cg.addEdge(newEdge);
                                        addSize++;
                                    }
                                }
                            }
                        }
                    }
                } catch (RuntimeException e) {

                }
            }
        }
        Logger.i(TAG, "Add "+addSize+" edges");

        // update the cg with an improved one by adding some new edges;
        Scene.v().setCallGraph(cg);

    }

    /**解析所有类文件，识别出app的类文件，以及R类文件防止反编译得到的xml文件不可用*/
    private void resolveAllClasses() {
        Logger.i(TAG, "==============    Resolve and Exclude Class    ==============");
        boolean flag = false;
        Chain<SootClass> classChain = Scene.v().getClasses();
        if (launchActivityName.startsWith(pkg) || launchActivityName.startsWith(".")) {
            flag = true;
            for (SootClass s : classChain) {
                if (s.getName().startsWith(pkg))
                    allClasses.add(s);
                //collect R$id, R$string, R$menu, R$layout, R$xml
                //Todo 这里添加了所有的 R$xx ，是否只需要考虑 pkg.R$xx
                if (s.getName().endsWith("R$id")
                        || s.getName().endsWith("R$string")
                        || s.getName().endsWith("R$xml")
                        || s.getName().endsWith("R$menu")
                        || s.getName().endsWith("R$layout")
                        || s.getName().endsWith("R$navigation")) {
                    rClasses.add(s);
//                    Logger.i(TAG, s.getName());
                }
            }
        } else {
            flag = false;
            for (SootClass s : classChain) {
                if (!ExcludePackage.v().isExclude(s)) // isExclude()' false value means this SootClass is the app's source;
                    allClasses.add(s);
                //collect R$id, R$string, R$menu, R$layout, R$xml
                //Todo 这里添加了所有的 R$xx ，是否只需要考虑 pkg.R$xx
                if (s.getName().endsWith("R$id")
                        || s.getName().endsWith("R$string")
                        || s.getName().endsWith("R$xml")
                        || s.getName().endsWith("R$menu")
                        || s.getName().endsWith("R$layout")
                        || s.getName().endsWith("R$navigation")) {
                    rClasses.add(s);
//                    Logger.i(TAG, s.getName());
                }
            }
        }
        if (flag)
            Logger.i(TAG, "--------- Exclude classes by using package names...");
        else
            Logger.i(TAG, "--------- Exclude classes by using ExcludePakage.txt file...");
    }
    /**收集不同的类文件*/
    private void collectElement() {
        Logger.i(TAG, "============= Classify classes ============");
        //collect activity
        for (String act : activityNames) {
			Logger.i("[AppParser]",act);
            try {
                SootClass actClass = Scene.v().forceResolve(act, SootClass.BODIES);
                Scene.v().loadClass(act,SootClass.BODIES);
                if (actClass.getPackageName().startsWith(pkg) && actClass.getMethods().size() > 0) {
                    activities.add(actClass);
                } else {
                    Logger.i(TAG, "activity [" + act + "] is empty,try to splice the package name");
                    String actName = pkg + "." + act;
                    actClass = Scene.v().getSootClassUnsafe(actName);
                    if (actClass != null && actClass.getMethods().size() > 0) {
                        activities.add(actClass);
                    } else {
                        Logger.i(TAG, "collect activity exception, could not find the activity[" + act + "]");
                    }
                }
            } catch (RuntimeException e) {
                Logger.e(TAG, new AppParseException("extrac activity[" + act + "] fail!", e));
            }
        }
        for (SootClass sc : allClasses) {
            //collect fragments
            if (ClassService.isFragment(sc)) {
                fragments.add(sc);
            }
            if (ClassService.isViewModel(sc)){
                viewmodels.add(sc);
            }
            // collect the class generated by ButterKnife
            // it is a plugin used in XXXActivity.java (An annotation of "@BindView(R.id.XXX)" used to assign widgets),
            // which means system will automatically generate an extra XXXActivity_ViewBinding.java for helping XXXActivity.java realize findViewById()
            // Note:In XXXActivity.java, using the sentence: ButterKnife.bind(this); send to _ViewBinding;
            // 		In XXXActivity_ViewBinding.java, there is a field object of XXXActivity.java called target assigned with the "this" value.
//            if (sc.getName().endsWith("_ViewBinding")) {
//                ButterKnife_viewBindings.add(sc);
//            }
            //   还需要考虑 ViewBinding
            // ViewBinding technique will generate an extra XXXBinding.java from XXX.xml
            // (e.g., activity_main.xml generates ActivityMainBinding.java) for helping XXXActivity.java assign widgets.
        }
        Logger.i(TAG, "--------- Successful");
    }



    //////////////////////////////////////////////////
    //         Provide resource information         //
    //////////////////////////////////////////////////

    /** 对于每个activity (同时包括静态注册的fragment)，获取到布局文件。然后构建对应的界面层次树ACT,并加入到列表中*/
    private void buildLayoutInformation() {
        Logger.i(TAG, "========== buildLayoutInformation =========");
        //System.out.println(activities.size());
        List<SootClass> UIcontrollers = new ArrayList<>();
        UIcontrollers.addAll(activities);
        UIcontrollers.addAll(fragments);
        for (SootClass activityClass : UIcontrollers) { // 简便称，UI控制器都称之为activity
            int index = UIcontrollers.indexOf(activityClass);
            Logger.i("" + index, ": " + activityClass.getName());
            ActivityLayoutComponentTree ACT = null;
            SootMethod sootMethod = null;
            if (ClassService.isActivity(activityClass)) {
                sootMethod = activityClass.getMethodByNameUnsafe("onCreate");
                AXmlNode layoutRoot = parseLayoutFileOnCreateForActivity(sootMethod);
                if (layoutRoot!=null){
                    ACT = buildComponentTree(activityClass, layoutRoot, false);
                }
            }
            else if (ClassService.isFragment(activityClass)) {
                sootMethod = activityClass.getMethodByNameUnsafe("onCreateView");
                AXmlNode layoutRoot = parseLayoutFileOnCreateViewForFragment(sootMethod);
                if (layoutRoot!=null){
                    ACT = buildComponentTree(activityClass, layoutRoot, true);
                }

            }
            else {
                System.out.println("\t\tFail, cannot find onCreate() or onCreateView() ");
                continue;
            }

            if (ACT!=null){
                activityComponentTrees.add(ACT);
            }

        }   // Activity ends;

    }


    /**解析activity的onCreate方法，寻找setContentView()的布局文件设置
     * @return 失败返回null；成功返回布局文件根节点*/
    private AXmlNode parseLayoutFileOnCreateForActivity(SootMethod onCreate) {
        if (onCreate != null && MethodService.tryRetrieveActiveBody(onCreate) ) {
            Body body = onCreate.retrieveActiveBody();
            UnitGraph unitGraph = new BriefUnitGraph(body);
            MHGDominatorsFinder<Unit> df = new MHGDominatorsFinder(unitGraph);

            boolean has_setContentView = false;
            for (Unit unit : body.getUnits()) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    if (invokeExpr instanceof VirtualInvokeExpr){
                        SootMethod invokeMethod = invokeExpr.getMethod();

                        if (invokeMethod.getName().equals("setContentView")) {
                            has_setContentView = true;
                            Value value = invokeExpr.getArg(0);    // setContentView() only have one para
//								System.out.println(unit);
//								System.out.println(invokeMethod.getDeclaringClass());
//								System.out.println(invokeMethod.getDeclaration());
//								System.out.println(sc.getName());

                            if (value.getType().toString().equals("int") ) {    // 参数为布局文件资源ID，即R.layout.xxx
                                if (value instanceof Local && !(value instanceof Constant))
                                    return null;
                                int layoutID = Integer.valueOf(value.toString());

                                String layoutXmlName = getLayoutNameById(layoutID);
                                if (layoutXmlName!=null){
                                    String layoutPath = "res/layout/" + layoutXmlName + ".xml";
                                    try {
                                        InputStream is = apkHandler.getInputStream(layoutPath);
                                        if (is != null) {
                                            AXmlHandler aXmlHandler = new AXmlHandler(is);
                                            AXmlDocument aXmlDocument = aXmlHandler.getDocument();
                                            AXmlNode root = aXmlDocument.getRootNode();
                                            System.out.println("\t\tSuccessful !");
                                            return root;
                                        }
                                    } catch (IOException e) {
                                        System.out.println("find corresponding layout.xml fails");
                                        e.printStackTrace();
                                    }
                                }

                            }
                            else if (isViewType(Scene.v().forceResolve(value.getType().toString(), SootClass.SIGNATURES))) {
                                // 参数为一个View对象
                                // 另一种布局引入,处理 ViewBinding
                                List<Unit> preUnits = df.getDominators(unit);
                                for (int i = preUnits.size() - 2; i >= 0; i--) {    // 第一个是自己,忽略
                                    Unit preUnit = preUnits.get(i);
                                    Stmt preStmt = (Stmt) preUnit;
                                    if (preStmt instanceof AssignStmt) {
                                        if (((AssignStmt) preStmt).getLeftOp() == value) {  // 在前驱中找到setContentView()中的参数的赋值语句
                                            Value rightOp = ((AssignStmt) preStmt).getRightOp();
                                            if (rightOp.getUseBoxes().size() == 1) {
                                                Value useValue = rightOp.getUseBoxes().get(0).getValue();
                                                Type localUseType = useValue.getType();
                                                Pattern rule = Pattern.compile(pkg + "\\.databinding\\..+Binding");
                                                Matcher matcher = rule.matcher(localUseType.toString());
                                                if (matcher.matches()) {    // 匹配成功，证明是ViewBinding
                                                    String localUseTypeString = localUseType.toString();
                                                    String ViewBindingName = localUseTypeString.substring(localUseTypeString.lastIndexOf(".") + 1);
                                                    String layoutXmlName = praseViewBindingName(ViewBindingName);
                                                    String layoutPath = "res/layout/" + layoutXmlName + ".xml";
                                                    try {
                                                        InputStream is = apkHandler.getInputStream(layoutPath);
                                                        if (is != null) {
                                                            AXmlHandler aXmlHandler = new AXmlHandler(is);
                                                            AXmlDocument aXmlDocument = aXmlHandler.getDocument();
                                                            AXmlNode root = aXmlDocument.getRootNode();
                                                            System.out.println("\t\tSuccessful !");
                                                            return root;
                                                        }
                                                    } catch (IOException e) {
                                                        System.out.println("find corresponding layout.xml fails");
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            break;    // end loop; no need to look backward for assignStmt;
                                        }
                                    }
                                }
                            }   // end else if
                            break;
                        }// 找到了setContentView调用

                    }
                }

            }
            if (has_setContentView == false) {
                // 没有setContentView()，可能是直接用 Fragmrent 替换当前activity的根布局 android.R.id.content = 16908290
                // 例如：getSupportFragmentManager()
                //        .beginTransaction()
                //        .replace(android.R.id.content, settingsFragment)
                //        .commit();

//                    System.out.println("\t\tCannot find setContentView()!! try to find \"android.R.id.content\" ");
//                    for (Unit unit : body.getUnits()){
//                        Stmt stmt = (Stmt) unit;
//                        if (stmt.containsInvokeExpr()){
//                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
//                            if (invokeExpr instanceof VirtualInvokeExpr){
//                                SootMethod invokee = invokeExpr.getMethod();
//                                if (invokee.getSubSignature().equals("androidx.fragment.app.FragmentTransaction replace(int,androidx.fragment.app.Fragment)")){
//                                    if (invokeExpr.getArg(0).toString()=="16908290"){
//                                        Value value = invokeExpr.getArg(1);
//                                        for (SootClass fragmentClass : fragments){
//                                            if (fragmentClass.getName().equals(value.getType().toString())){
//                                                SootMethod onCreateView = fragmentClass.getMethodUnsafe("android.view.View onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)");
//                                                AXmlNode fragmentRoot = parseOnCreateView(onCreateView);
//                                                ACT = buildComponentTree(activityClass, fragmentRoot, true);
//                                                break;
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
                ;
                System.out.println("\t\tFail, cannot find setContentView() !!"); //  and "android.R.id.content"
            }
        }
        return null;
    }


    /**解析fragment的onCreateView()方法，寻找setContentView()的布局文件设置
     * @return 失败返回null；成功返回布局文件根节点*/
    private AXmlNode parseLayoutFileOnCreateViewForFragment(SootMethod onCreateView) {
        if (onCreateView!=null && MethodService.tryRetrieveActiveBody(onCreateView)){
            Body body = onCreateView.retrieveActiveBody();
            boolean hasInflate = false;
            for (Unit unit : body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    if (invokeExpr instanceof VirtualInvokeExpr){
                        SootMethod invokee = invokeExpr.getMethod();

                        if (invokee.getName().equals("inflate")){
                            /* <android.view.LayoutInflater: android.view.View inflate(int,android.view.ViewGroup,boolean)>(2131427356, $r2, 0) */
                            Value value = invokeExpr.getArg(0);
                            hasInflate = true;
                            if (value.getType().equals(IntType.v()) && value instanceof Constant ){
                                int layoutID = Integer.valueOf(value.toString());
                                String layoutXmlName = AppParser.v().getLayoutNameById(layoutID);
                                if (layoutXmlName!=null){
                                    String layoutPath = "res/layout/" + layoutXmlName + ".xml";
                                    try {
                                        InputStream is = apkHandler.getInputStream(layoutPath);
                                        if (is != null) {
                                            AXmlHandler aXmlHandler = new AXmlHandler(is);
                                            AXmlDocument aXmlDocument = aXmlHandler.getDocument();
                                            AXmlNode fragmentRoot = aXmlDocument.getRootNode();
                                            System.out.println("\t\tSuccessful !");
                                            return fragmentRoot;
                                        }
                                    } catch (IOException e) {
                                        Logger.i(TAG+" parseOnCreateView","寻找fragment布局文件出错！");
                                        e.printStackTrace();
                                    }
                                }

                            }
                            break;
                        }
                    }
                }

            }
            if (hasInflate == false){
                System.out.println("\t\tonCreateView() 找不到inflate()布局填充");
            }
        }
        return null;
    }

    /**解析xml文件节点，利用ACT构造函数 构建ACT
     * @param activityClass activity or fragment 的类
     * @param root 布局文件根节点
     * @param isFragment 是否是fragment
     * @return 返回利用 活动组件树 构造方法生成的组件树信息*/
    private ActivityLayoutComponentTree buildComponentTree(SootClass activityClass, AXmlNode root, boolean isFragment) {
//		System.out.println(root);
//		System.out.println(root.getTag());
//		for (AXmlNode child: root.getChildren()){
//			System.out.println(child.getAttributes());
//		}
//        if (isFragment==true){
//            Widget fragment = new Widget();
//            fragment.setType("fragment");
//            fragment.setParent(null);
//            fragment.setH(0);   // 根
//            ActivityLayoutComponentTree ACT = new ActivityLayoutComponentTree(activityClass, root, fragment);
//            return ACT;
//        }else {
        ActivityLayoutComponentTree ACT = new ActivityLayoutComponentTree(activityClass, root, isFragment);
        return ACT;
//        }


    }

    private boolean isViewType(SootClass sootClass) {
        SootClass superclass = sootClass;
        while (superclass != null) {
            if (superclass.getName().equals("android.view.View"))
                return true;
            superclass = superclass.getSuperclassUnsafe();
        }
        return false;
    }

    private String praseViewBindingName(String viewBindingName) { // ActivityMainBinding -> activity_main
        char[] chars = viewBindingName.substring(0, viewBindingName.lastIndexOf("Binding")).toCharArray();
        String result = String.valueOf(chars[0]);
        for (int i = 1; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z')
                result = result + "_";
            result = result + String.valueOf(chars[i]);
        }
        //System.out.println(result.toLowerCase());
        return result.toLowerCase();
    }

    /////////////////  Resources  //////////////////
    public String getWidgetNameById(int id) /*throws RecourseMissingException*/ {
        String res = ResConvertor.v().getWidgetNameById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Widget id: " + id);
    }

    public String getWidgetIdByName(String name) /*throws RecourseMissingException*/ {
        String res = ResConvertor.v().getWidgetIdByName(name);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Widget name: " + name);
    }

    public String getLayoutNameById(int id)  /*throws RecourseMissingException*/  {
        String res = ResConvertor.v().getLayoutNameById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Layout id: " + id);
    }

    public String getXmlNameById(int id)  /*throws RecourseMissingException*/ {
        String res = ResConvertor.v().getXmlNameById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Xml id: " + id);
    }

    public String getMenuNameById(int id)  /*throws RecourseMissingException*/ {
        String res = ResConvertor.v().getMenuNameById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Menu id: " + id);
    }

    public String getNavigationNameById(int id) /*throws RecourseMissingException*/  {
        String res = ResConvertor.v().getNavigationNameById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("Navigation id: " + id);
    }

    public String getStringValueById(int id)  /*throws RecourseMissingException*/  {
        String res = ResConvertor.v().getStringValueById(id);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("String id: " + id);
    }

    public String getStringValueByName(String name)  /*throws RecourseMissingException*/ {
        String res = ResConvertor.v().getStringValueByName(name);
        //if (res != null)
            return res;
        //throw new RecourseMissingException("String name: " + name);
    }


    /**
     * parse R class or public.xml & strings.xml, and get res name by res id
     */
    private static class ResConvertor {
        private static final String MTAG = "[ResConvertor]";

        public static void reset() {
            instance = null;
        }

        private static ResConvertor instance = null;

        private ResConvertor() {
        }

        public static ResConvertor v() {
            if (instance == null) {
                synchronized (ResConvertor.class) {
                    if (instance == null) {
                        instance = new ResConvertor();
                    }
                }
            }
            return instance;
        }

		/* 	In Android Studio, we define widget by the following two attributes
			[1] android:id="@+id/resName"
		 	[2] android:text="@string/button1"
		 	In the following Map, here [1] means name, and [2] means value.
		 	  for example:	In Jimple codes: $r5 = virtualinvoke r0.<com.example.viewmedeltest.MainActivity: android.view.View findViewById(int)>(2131165355);
		 	  				findViewByID() use id-number(2131165355,we call it <id>) as a parameter to find Button widget,
		 					here we can get resName "button1" in [1] by query Map <WIDGET_id_name>, we call it <name> ;
		 					then get the text of button1 is "+1" in [2] by query Map <STRING_name_value>, we call it value.  */

        /**
         * String字符串的 资源名 和 值 的映射
         */
        private Map<String, String> STRING_name_value = new HashMap<String, String>();
        /**
         * String字符串的 资源Id 和 资源名 的映射
         */
        private Map<String, String> STRING_id_name = new HashMap<String, String>();
        /**
         * 控件Id的 资源Id 和 资源名 的映射
         */
        private Map<String, String> WIDGET_id_name = new HashMap<String, String>();
        /**
         * 布局文件的 资源Id 和 文件名 的映射
         */
        private Map<String, String> LAYOUTFILE_id_name = new HashMap<String, String>();
        /**
         * xml文档的 资源Id 和 文件名 的映射
         */
        private Map<String, String> XMLFILE_id_name = new HashMap<String, String>();
        /**
         * 菜单布局文件的 资源Id 和 文件名 的映射
         */
        private Map<String, String> MENUFILE_id_name = new HashMap<String, String>();
        /**
         * Navigation文件的 资源Id 和 文件名 的映射
         */
        private Map<String, String> NAVIGATIONFILE_id_name = new HashMap<String, String>();

        public void parse(String apkDir, String apkName, List<SootClass> rClasses) {
            Logger.i(TAG + MTAG, "===========================");
            // 解析public.xml或rClass， 获得资源Id和资源名的映射。[public.xml本质就是一个所有的rClass里的所有数据的集合]
            // 区别：public.xml 里的是0xffffffff的十六进制；	rClass 里的是1234567890的十进制
            File publicXMLFile = new File(apkDir + apkName, "decompile/res/values/public.xml");
            if (!publicXMLFile.exists() || !publicXMLFile.isFile()) {
                // 如果public.xml文件不存在，解析前面收集的rClass
                Logger.e(TAG + MTAG, new RecourseMissingException("No public.xml (" + publicXMLFile.getAbsolutePath() + ")"));
                parseRClass(rClasses);
            } else {
                // 否则，若存在，则直接解析此public.xml文件
                parsePublicXmlFile(publicXMLFile);
            }
            //解析strings.xml， 获得字符串的名字与值的映射
            File stringsXMLFile = new File(apkDir + apkName, "decompile/res/values/strings.xml");
            if (!stringsXMLFile.exists() || !stringsXMLFile.isFile()) {
                Logger.e(TAG + MTAG, new RecourseMissingException("No strings.xml (" + stringsXMLFile.getAbsolutePath() + ")"));
//				return;
            } else
                parseStringsXml(stringsXMLFile);
            //输出资源信息
            write();
        }

        private void parsePublicXmlFile(File publicXMLFile) {
            Logger.i(TAG + MTAG, "Parse public.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try (FileInputStream fis = new FileInputStream(publicXMLFile);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(bis);
                NodeList nodeList = document.getElementsByTagName("public");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) nodeList.item(i);
                        if (element.hasAttribute("type")) {
                            String type = element.getAttribute("type");
                            if (type.equals("layout") || type.equals("menu")
                                    || type.equals("string") || type.equals("xml")
                                    || type.equals("id") || type.equals("navigation")) {
                                String name = element.getAttribute("name");
                                String id = "0 " + name;
                                String idHex = element.getAttribute("id");
                                if (idHex != null) {
                                    int idDec = Integer.parseInt(idHex.substring(2), 16);
                                    id = String.valueOf(idDec);
                                }
                                switch (type) {
                                    case "layout":
                                        LAYOUTFILE_id_name.put(id, name);
                                        break;
                                    case "menu":
                                        MENUFILE_id_name.put(id, name);
                                        break;
                                    case "string":
                                        STRING_id_name.put(id, name);
                                        break;
                                    case "xml":
                                        XMLFILE_id_name.put(id, name);
                                        break;
                                    case "id":
                                        WIDGET_id_name.put(id, name);
                                        break;
                                    case "navigation":
                                        NAVIGATIONFILE_id_name.put(id, name);
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                }
                Logger.i(TAG + MTAG, "Parse public.xml succeful!!");
            } catch (Exception e) {
                Logger.e(TAG + MTAG, new RecourseMissingException("Parse public.xml fail", e));
            }
        }

        private void parseRClass(List<SootClass> rClasses) {
            Logger.i(TAG + MTAG, "Parse R.class");
            for (SootClass rClass : rClasses) {
                if (rClass.getName().endsWith("R$id")) {
                    //解析 R$id
                    SootClass rIdClass = rClass;
                    Iterator<SootField> ids = rIdClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                WIDGET_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
                //解析 R$layout
                if (rClass.getName().endsWith("R$layout")) {
                    SootClass rLayoutClass = rClass;
                    Iterator<SootField> ids = rLayoutClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                LAYOUTFILE_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
                //解析 R$xml
                if (rClass.getName().endsWith("R$xml")) {
                    SootClass rLayoutClass = rClass;
                    Iterator<SootField> ids = rLayoutClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                XMLFILE_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
                //解析 R$string
                if (rClass.getName().endsWith("R$string")) {
                    SootClass rLayoutClass = rClass;
                    Iterator<SootField> ids = rLayoutClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                STRING_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
                //解析 R$menu
                if (rClass.getName().endsWith("R$menu")) {
                    SootClass rLayoutClass = rClass;
                    Iterator<SootField> ids = rLayoutClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                MENUFILE_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
                //解析 R$navigation
                if (rClass.getName().endsWith("R$navigation")) {
                    SootClass rLayoutClass = rClass;
                    Iterator<SootField> ids = rLayoutClass.getFields().iterator();
                    while (ids.hasNext()) {
                        SootField idField = ids.next();
                        if (idField.isFinal() && idField.isStatic()) {
                            String name = idField.getName();
                            Tag fieldTag = idField.getTag("IntegerConstantValueTag");
                            if (fieldTag != null) {
                                String tagString = fieldTag.toString();
                                String fieldValue = tagString.split(" ")[1];
                                NAVIGATIONFILE_id_name.put(fieldValue, name);
                            }
                        }
                    }
                }
            }
        }

        private void parseStringsXml(File stringsXMLFile) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try (FileInputStream fis = new FileInputStream(stringsXMLFile);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(bis);
                NodeList nodeList = document.getElementsByTagName("string");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) nodeList.item(i);
                        if (element.hasAttribute("name")) {
                            //Logger.i(element.getAttribute("name"),element.getTextContent());
                            STRING_name_value.put(element.getAttribute("name"), element.getTextContent());
                        }
                    }
                }
                Logger.i(TAG + MTAG, "Parse strings.xml succeful!!");
            } catch (Exception e) {
                Logger.e(TAG + MTAG, new RecourseMissingException("Parse strings.xml fail", e));
            }
        }

        private void write() {
            //输出这些资源
            IOService.v().writeResources(WIDGET_id_name, LAYOUTFILE_id_name, XMLFILE_id_name, MENUFILE_id_name, NAVIGATIONFILE_id_name);
            //strings.xml
            IOService.v().writeString(STRING_id_name, STRING_name_value);
        }

        /**从Id查询得到name，再用name查询得到value*/
        public String getStringValueById(int id) {
            String idStr = String.valueOf(id);
            String stringName = STRING_id_name.get(idStr);
            if (stringName == null) {//maybe id systemRes
                stringName = SystemicResouces.getStringNameById(id);
            }
            if (stringName != null) {
                String stringValue = STRING_name_value.get(stringName);
                if (stringValue == null)
                    stringValue = SystemicResouces.getStringByStringName(stringName);
                return stringValue;
            }
            return null;
        }

        public String getStringValueByName(String name) {
            return STRING_name_value.get(name);
        }

        public String getWidgetNameById(int id) {
            String idStr = String.valueOf(id);
            String widgetName = WIDGET_id_name.get(idStr);
            if (widgetName == null) {//may be is system res
                widgetName = SystemicResouces.getWidgetNameById(id);
            }
            return widgetName;
        }

        public String getWidgetIdByName(String name) {
            for (String key : WIDGET_id_name.keySet()){
                if (WIDGET_id_name.get(key).equals(name)){
                    return key;
                }
            }
            return null;
        }

        public String getLayoutNameById(int id) {
            String idStr = String.valueOf(id);
            return LAYOUTFILE_id_name.get(idStr);
        }

        public String getXmlNameById(int id) {
            String idStr = String.valueOf(id);
            return XMLFILE_id_name.get(idStr);
        }

        public String getMenuNameById(int id) {
            String idStr = String.valueOf(id);
            return MENUFILE_id_name.get(idStr);
        }

        public String getNavigationNameById(int id) {
            String idStr = String.valueOf(id);
            return NAVIGATIONFILE_id_name.get(idStr);
        }
    }
    ///////////////////////////////////////


    ///////////////  other  ///////////////
    /**代码行数计算*/
    public int loc() {
        int loc = 0;
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (!sc.isPhantom() && !Utils.isClassInSystemPackage(sc.getName())) {
                for (SootMethod sm : sc.getMethods()) {
                    if (sm.hasActiveBody()) {
                        loc += sm.getActiveBody().getUnits().size();
                    }
                }
            }
        }
        return loc / 1000;
    }

    public boolean isAppClass(String clsName) {
        for (SootClass sc : allClasses) {
            if (sc.getName().equals(clsName)) {
                return true;
            }
        }
        return false;
    }

    public String getActivityByImplicitIntent(String action, String type, Set<String> categories) {
        for (Activity implicitActivity : implicitActivities) {
            if (implicitActivity.match(action, type, categories))
                return implicitActivity.mClassName;
        }
        return null;
    }


    private class DecompileException extends RuntimeException {
        public DecompileException(String msg, Throwable t) {
            super(msg, t);
        }
    }

    private class AppParseException extends RuntimeException {
        public AppParseException(String msg, Throwable t) {
            super(msg, t);
        }

    }

    ///////////////////////////////////////


    ///////////////  Test  ////////////////

/*
    public static void main(String[] args) {
        AppParser testApp = AppParser.v();
        //testApp.init("D:\\AppsForFDLDroidTest\\APK\\", "org.kde.bettercounter_10900", isOutputJimple);//org.kde.bettercounter_10900    eu.faircode.email_1769
        testApp.getActivityComponentTrees();
//		CallGraph cg = testApp.getCg();
//		System.out.println(cg.size());
//		printCG(cg);
        System.out.println("程序执行完成！");
    }
*/

    private static void printCG(CallGraph cg) {
        StringBuilder sb = new StringBuilder();
        Iterator<Edge> iter = cg.iterator();
        while (iter.hasNext()) {
            Edge e = iter.next();
            sb.append(e.src().getSignature());
            sb.append("\n-->");
            sb.append(e.tgt().getSignature());
            sb.append("\n\n");
        }
        try {
            File f1 = new File("D:\\AppsForFDLDroidTest\\Output", "CG_app-release.txt");
            if (!f1.exists())
                f1.createNewFile();
            FileWriter out1 = new FileWriter(f1, false);
            out1.write(sb.toString());
            out1.close();
        } catch (IOException e) {

        }
    }

}
