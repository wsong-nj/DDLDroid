package tool.entryForAllApks;

import org.xmlpull.v1.*;
import soot.*;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import soot.toolkits.scalar.ConstantValueToInitializerTransformer;
import tool.analysisForTwoCase.AnalysisStart;
import tool.basicAnalysis.AppParser;
import tool.utils.ExcludePackage;
import tool.utils.MethodService;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EntryForAll {
    //private static final String TAG = "[App]";

    private String apkFileDirectory; // APK fileS directory
    private String androidJAR; // Android JAR path
    private String jimpleOutputDir;

    private boolean isOutputJimple;
    private ArrayList<String> allApkFilePathList; // All APK file path list
    private static CallGraph callGraph;
    private long runningTime;

    private long loc;

    public EntryForAll(String[] args, boolean isOutputJimple) {
        this.apkFileDirectory = args[0];
        this.androidJAR = args[1];
        this.jimpleOutputDir = args[2];

        this.isOutputJimple = isOutputJimple;
        this.allApkFilePathList = new ArrayList<String>();
    }

    public ArrayList<String> getAllApkFilespath() { // Get APK files, return APK path
        File f = new File(this.apkFileDirectory);
        File[] list = f.listFiles();
        String filePath, fileExtension;
        for (int i = 0; i < list.length; i++) {
            filePath = list[i].getAbsolutePath(); // list element's format is  /D:\...\CameraView_apkpure.com.apk
            int index1 = filePath.lastIndexOf(".");
            int index2 = filePath.length();
            fileExtension = filePath.substring(index1 + 1, index2);
            if (fileExtension.equals("apk")) { // If the file is APK
                this.allApkFilePathList.add(filePath);
            }
        }
        return allApkFilePathList;
    }

    public void analyzeAll(ArrayList<String> allApksPathList) throws IOException, XmlPullParserException {
        String apkFileLocation; // Current APK's whole location, including \name.apk
        String curAppName; // Current APK name

        long start = System.currentTimeMillis(); // Time when it starts
        int selectedApksCount = allApksPathList.size();
        //int selectedApksCount = 1;  // test for one app
        for (int i = 0; i < selectedApksCount; i++) {

            apkFileLocation = allApksPathList.get(i).toString();
            curAppName = apkFileLocation.substring(apkFileLocation.lastIndexOf("\\") + 1, apkFileLocation.lastIndexOf("."));
            System.out.println("*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*");
            System.out.println("App count: No." + (i + 1));
            System.out.println("App file path: " + apkFileLocation);
            System.out.println("App name: " + curAppName);

//            sootInit(apkFileLocation, curAppName, androidJAR, isOutputJimple); // New Soot Initialization
//
//            Chain<SootClass> test= Scene.v().getClasses();
//            for (SootClass t1:test){
//                if (t1.getName().contains("SecondActivity$1")){
//                    System.out.println(t1.getName());
//                    System.out.println(t1.getMethods());
//                    SootMethod sootMethod = t1.getMethodByNameUnsafe("onClick");
//                    Body body = sootMethod.retrieveActiveBody();
//                    System.out.println(body);
//                }
//
//            }

            if (isOutputJimple == false){   // no need to Output Jimple codes, so then analyze current App
                long startForOneApp = System.currentTimeMillis();
                //  应用解析器，进行反编译、读Manifest、获取资源文件等等
                String apkDir = apkFileLocation.substring(0,apkFileLocation.lastIndexOf("\\") + 1); // 格式是 D:\AppsForFDLDroidTest\APK\
                AppParser.v().init(apkDir,curAppName,androidJAR); // Soot and FlowDroid inits here;

                AnalysisStart analysisStart = new AnalysisStart(apkDir, curAppName, AppParser.v().getCg());
                analysisStart.analyzeAPk(startForOneApp);
                System.out.println();

                AppParser.reset();
            }
            else {  // isOutputJimple = true; Jimple codes generating
                sootInit(apkFileLocation, curAppName, androidJAR);
                //sootIni(apkFileLocation, curAppName, androidJAR,false);
            }
        }
        //excel.WriteAll(); // Write all line into excel
        long end = System.currentTimeMillis();
        runningTime = (end - start) / 1000; // Running Time(:s)
        System.out.println("================================================================================");
        System.out.println("It takes " + runningTime + " seconds to analyze all these " + selectedApksCount + " apps.");
    }


    public void sootInit(String apkFileLocation, String apkName, String androidJar){
        G.reset();
        //the path to the APK
        Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
        //prefer Android APK files, This option instructs Soot to load Android APK files.// -src-prec apk
        Options.v().set_src_prec(Options.src_prec_apk);
        //the path to the platform JAR files you downloaded earlier
        if (androidJar.contains("android.jar"))
            Options.v().set_force_android_jar(androidJar);
        else
            Options.v().set_android_jars(androidJar);
        //prepend the VM's classpath to Soot's own classpath
        Options.v().set_prepend_classpath(true);
        if (isOutputJimple) {
            //output format //–f. This option instructs Soot to produce a jimple file as output
            Options.v().set_output_format(Options.output_format_jimple);
            //System.out.println("Jimple codes of "+apkName+" are generated at: "+jimpleOutputDir + apkName);
            Options.v().set_output_dir( jimpleOutputDir + apkName);

        }
        else
            Options.v().set_output_format(Options.output_format_none);

        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_multiple_dex(true); // Process all DEX files found in APK.
        Options.v().set_keep_line_number(true);

        List<String> excludeList = new LinkedList<String>();
        ExcludePackage.v().setPkgName("");
        excludeList.addAll(ExcludePackage.v().excludes);
        Options.v().set_exclude(excludeList);

        Options.v().set_no_bodies_for_excluded(true);
        Scene.v().loadNecessaryClasses();

        //PackManager.v().runPacks(); // for jimple output write
        if (isOutputJimple){
            boolean countLoC = false;
            if ( countLoC==false ){
                File jimpleFileDir = new File(jimpleOutputDir + apkName);
                if (jimpleFileDir.exists() && jimpleFileDir.isDirectory()){
                    File[] files = jimpleFileDir.listFiles();
                    if (files.length==0)
                        jimpleFileDir.delete();
                    else{
                        for (File element : files)
                            if (element.isFile())
                                element.delete();
                    }
                }
                // if choose to output jimple files, we cannot process following analysis due to we cannot get Body any more;
                PackManager.v().writeOutput();
                System.out.println("Jimple codes has been created at "+jimpleOutputDir+apkName);
                return;
            }
            else{
                loc = 0;
                try {
                    ProcessManifest processManifest = new ProcessManifest(apkFileLocation);
                    String pkg = processManifest.getPackageName();
                    if (apkName.contains("Simpletask"))
                        pkg = pkg.substring(0,24);
                    for (SootClass sc : Scene.v().getApplicationClasses()){
                        if (sc.getName().contains(pkg))
                            for (SootMethod sm : sc.getMethods()){
                                if (MethodService.tryRetrieveActiveBody(sm)){
                                    loc += sm.retrieveActiveBody().getUnits().size();
                                }
                            }
                    }
                    System.out.printf("%.2fk\n",loc*1.0/1000);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                }

            }
        }
        else {
            for (SootClass sc : Scene.v().getApplicationClasses()){
                if (!sc.isPhantom())
                    ConstantValueToInitializerTransformer.v().transformClass(sc); // 对于<clinit>方法中的静态值加载
            }
        }

    }

    /**/
    public static void initSoot(String[] param)/* throws IOException, XmlPullParserException*/ {
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_format(Options.output_format_jimple);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_output_dir("JimpleOutput");
        Options.v().set_keep_line_number(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_force_android_jar(param[1]);
        Options.v().set_process_dir(Collections.singletonList(param[3]));
        Options.v().set_whole_program(true);
        Options.v().set_force_overwrite(true);
        Scene.v().loadNecessaryClasses();	// Load necessary classes
        CHATransformer.v().transform(); //Call graph
        callGraph=Scene.v().getCallGraph();
        //JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
        //info = new InfoflowCFG(icfg);
        System.out.println("callgraph size:"+callGraph.size());

        Scene.v().addBasicClass("java.io.BufferedReader",SootClass.HIERARCHY);
        Scene.v().addBasicClass("java.lang.StringBuilder",SootClass.BODIES);
        Scene.v().addBasicClass("java.util.HashSet",SootClass.BODIES);
        Scene.v().addBasicClass("android.content.Intent",SootClass.BODIES);
        Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().addBasicClass("com.app.test.CallBack",SootClass.BODIES);
        Scene.v().addBasicClass("java.io.Serializable",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.io.Serializable",SootClass.BODIES);
        Scene.v().addBasicClass("android.graphics.PointF",SootClass.SIGNATURES);
        Scene.v().addBasicClass("android.graphics.PointF",SootClass.BODIES);
        Scene.v().addBasicClass("org.reflections.Reflections",SootClass.HIERARCHY);
        Scene.v().addBasicClass("org.reflections.scanners.Scanner",SootClass.HIERARCHY);
        Scene.v().addBasicClass("org.reflections.scanners.SubTypesScanner",SootClass.HIERARCHY);
        Scene.v().addBasicClass("java.lang.ThreadGroup",SootClass.SIGNATURES);
        Scene.v().addBasicClass("com.ironsource.mobilcore.OfferwallManager",SootClass.HIERARCHY);
        Scene.v().addBasicClass("bolts.WebViewAppLinkResolver$2",SootClass.HIERARCHY);
        Scene.v().addBasicClass("com.ironsource.mobilcore.BaseFlowBasedAdUnit",SootClass.HIERARCHY);
        Scene.v().addBasicClass("android.annotation.TargetApi",SootClass.SIGNATURES);
        Scene.v().addBasicClass("com.outfit7.engine.Recorder$VideoGenerator$CacheMgr",SootClass.HIERARCHY);
        Scene.v().addBasicClass("com.alibaba.motu.crashreporter.handler.CrashThreadMsg$",SootClass.HIERARCHY);
        Scene.v().addBasicClass("java.lang.Cloneable",SootClass.HIERARCHY);
        Scene.v().addBasicClass("org.apache.http.util.EncodingUtils",SootClass.SIGNATURES);
        Scene.v().addBasicClass("org.apache.http.protocol.HttpRequestHandlerRegistry",SootClass.SIGNATURES);
        Scene.v().addBasicClass("org.apache.commons.logging.Log",SootClass.SIGNATURES);
        Scene.v().addBasicClass("org.apache.http.params.HttpProtocolParamBean",SootClass.SIGNATURES);
        Scene.v().addBasicClass("org.apache.http.protocol.RequestExpectContinue",SootClass.SIGNATURES);
        Scene.v().loadClassAndSupport("Constants");
    }

    public static void sootIni(String apkFileLocation, String apkName, String androidJar, boolean isOutputJimple){ //师姐模板
        G.reset();

        Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
        //prefer Android APK files, This option instructs Soot to load Android APK files.// -src-prec apk
        Options.v().set_src_prec(Options.src_prec_apk);
        //output format //–f
        Options.v().set_force_android_jar(androidJar);

        //prepend the VM's classpath to Soot's own classpath
        Options.v().set_prepend_classpath(true);
        if (isOutputJimple) {
            //This option instructs Soot to produce a jimple file as output
            Options.v().set_output_format(Options.output_format_jimple);
            Options.v().set_output_dir("E:\\JimpleCodesLocation\\" + apkName);
        }
        else
            Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_keep_line_number(true);
        Options.v().process_multiple_dex();

        Scene.v().loadNecessaryClasses();

        PackManager.v().runPacks();

        if (isOutputJimple){
            PackManager.v().writeOutput();
            System.out.println("Jimple file generated.");
        }
        else {
            for (SootClass sc : Scene.v().getApplicationClasses()){
                if (!sc.isPhantom())
                    ConstantValueToInitializerTransformer.v().transformClass(sc);
            }
        }

    }


}
