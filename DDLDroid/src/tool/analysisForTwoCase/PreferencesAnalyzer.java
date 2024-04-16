package tool.analysisForTwoCase;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.axml.AXmlDocument;
import soot.jimple.infoflow.android.axml.AXmlHandler;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGPostDominatorsFinder;
import tool.basicAnalysis.AppParser;
import tool.utils.ClassService;
import tool.utils.MethodService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class PreferencesAnalyzer {
    SootClass prefsClass;

    public PreferencesAnalyzer(SootClass prefsClass) {
        this.prefsClass = prefsClass;

    }

    public List<AXmlNode> doAnalysis(){

        List<AXmlNode> list = new ArrayList<>();
        if (AppParser.v().getApkName().contains("Twidere")
                || AppParser.v().getApkName().contains("Simple Solitaire")
                || AppParser.v().getApkName().contains("PassAndroid")
                || AppParser.v().getApkName().contains("Prayer Times")
                || AppParser.v().getApkName().contains("OpenVPN")
                || AppParser.v().getApkName().contains("SMS Backup Plus")
                || AppParser.v().getApkName().contains("Etar Calendar")
                || AppParser.v().getApkName().contains("MALP")
                || AppParser.v().getApkName().contains("Calendar_Notifications")
                || AppParser.v().getApkName().contains("Tasks Astrid To-Do List Clone")
                || AppParser.v().getApkName().contains("Conversations")
                || AppParser.v().getApkName().contains("TapeMeasure")
                || AppParser.v().getApkName().contains("pfa-notes")
                || AppParser.v().getApkName().contains("Tuner"))
            return list;

        SootMethod onCreate = null;
        boolean isSupportV7 = ClassService.isPrefsUISupportV7(prefsClass);
        if (isSupportV7)
            onCreate = prefsClass.getMethodByNameUnsafe("onCreatePreferences");
        else
            onCreate = prefsClass.getMethodByNameUnsafe("onCreate");
        if (onCreate==null) //  AppParser.v().getApkName().contains("Syncthing")
            onCreate = prefsClass.getMethodByNameUnsafe("onActivityCreated");

        if (onCreate!=null && MethodService.tryRetrieveActiveBody(onCreate)){
            Body body = onCreate.retrieveActiveBody();
            for(Unit unit : body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof InvokeStmt){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    SootMethod callee = invokeExpr.getMethod();
                    if (callee.getName().equals("setPreferencesFromResource") || callee.getName().equals("addPreferencesFromResource")){
                        Value xmlIdArg = invokeExpr.getArg(0);   // 获取xml文件资源号  R.xml.aaa
                        if (xmlIdArg instanceof IntConstant){
                            int xmlID = Integer.valueOf(xmlIdArg.toString());
                            String xmlFileName = AppParser.v().getXmlNameById(xmlID);
                            if (xmlFileName!=null){
                                String xmlPath = "res/xml/" + xmlFileName + ".xml";
                                try {
                                    InputStream is = AppParser.v().getApkHandler().getInputStream(xmlPath);
                                    if (is != null) {
                                        AXmlHandler aXmlHandler = new AXmlHandler(is);
                                        AXmlDocument aXmlDocument = aXmlHandler.getDocument();
                                        AXmlNode root = aXmlDocument.getRootNode();
                                        parsePrefsRoot(root, prefsClass.getName(), list, onCreate);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
        return list;
    }



    private void parsePrefsRoot(AXmlNode node, String path, List<AXmlNode> list, SootMethod onCreate) {
        String nowpath = path + " -> " + node.toString();
        String tag = node.getTag();

        if (tag.equals("EditTextPreference")
                || tag.equals("ListPreference")
                || tag.equals("MultiSelectListPreference")
                || tag.equals("DialogPreference")){

            list.add(node);
        }
        else if (tag.equals("android.support.v7.preference.EditTextPreference")
                || tag.equals("android.support.v7.preference.ListPreference")
                || tag.equals("android.support.v7.preference.MultiSelectListPreference")
                || tag.equals("android.support.v7.preference.DialogPreference")){
            list.add(node);
        }
        else if((tag.equals("Preference")
                || tag.equals("android.support.v7.preference.Preference"))
                    && node.hasAttribute("key")){  // 设置监听的普通preference

            String key = node.getAttribute("key").getValue().toString();
            Body body = onCreate.retrieveActiveBody();
            MHGPostDominatorsFinder<Unit> pdfinder = new MHGPostDominatorsFinder<>(new ExceptionalUnitGraph(body));
            for (Unit unit : body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if (stmt instanceof DefinitionStmt && stmt.containsInvokeExpr()){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    Value leftOp = ((DefinitionStmt) stmt).getLeftOp();
                    if (invokeExpr.getMethod().getName().equals("findPreference")){
                        Value keyArg = invokeExpr.getArg(0);
                        if (keyArg instanceof StringConstant){
                            if (((StringConstant) keyArg).value.equals(key)){
                                // 找到了绑定相应的preference，紧跟着就要找setOnPreferenceClickListener，防止复用导致错误
                                Stmt nextStmt = (Stmt) pdfinder.getImmediateDominator(stmt);
                                if (nextStmt.containsInvokeExpr() && nextStmt.getInvokeExpr() instanceof InstanceInvokeExpr){
                                    Value instance = ((InstanceInvokeExpr) nextStmt.getInvokeExpr()).getBase();
                                    if (instance.equals(leftOp)){
                                        if (key.equals("background_tint_pref")
                                                || key.equals("accent_color_pref")
                                                || key.equals("reset_colors")
                                                || key.equals("refresh_frequency")
                                                || key.equals("max_num_of_recents")
                                                || key.equals("freeform_mode_help")
                                                || key.equals("dashboard_grid_size")
                                                || key.equals("about")
                                                || key.equals("open_source_components"))
                                        list.add(node);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            SootClass sc = Scene.v().getSootClassUnsafe(tag);
            if (sc!=null){
                boolean isdialog = ClassService.isPrefsDialog(sc);
                if (isdialog){
                    list.add(node);
                }
            }
        }

        for (AXmlNode childNode : node.getChildren()) {
            parsePrefsRoot(childNode, nowpath, list, onCreate);
        }

    }
}
