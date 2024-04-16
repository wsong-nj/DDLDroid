package tool.componentStructure;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.axml.AXmlDocument;
import soot.jimple.infoflow.android.axml.AXmlHandler;
import soot.jimple.infoflow.android.axml.AXmlNode;
import tool.basicAnalysis.AppParser;
import tool.basicAnalysis.RecourseMissingException;
import tool.staticGuiModel.Widget;
import tool.utils.Logger;

import java.io.IOException;
import java.io.InputStream;

public class ActivityLayoutComponentTree {
    /** 简称activity。UI控制器，activity或者fragment */
    private SootClass activity;
    private Widget rootWidget;

    public ActivityLayoutComponentTree(SootClass activity, AXmlNode root, boolean isFragment) {
        this.activity = activity;
        this.rootWidget = new Widget(root);
        rootWidget.setParent(null);
        rootWidget.setH(0);
        deepSearch(root,rootWidget,0);   //  深度优先遍历多叉树
    }

    public ActivityLayoutComponentTree(SootClass activity,AXmlNode root) {
        this.activity = activity;

        this.rootWidget = new Widget(root);
        rootWidget.setParent(null);
        rootWidget.setH(0);
        deepSearch(root,rootWidget,0);   //  深度优先遍历多叉树
    }

    public ActivityLayoutComponentTree(SootClass activity, AXmlNode fragmentRoot, Widget fragmentWidget) {
        this.activity = activity;
        // 根节点为一个fragment  -->  fragment根布局  -->  根布局里的所有控件
        this.rootWidget = fragmentWidget;
        Widget widget = new Widget(fragmentRoot);
        widget.setParent(rootWidget);
        widget.setH(this.rootWidget.getH()+1);
        this.rootWidget.addChildWidget(widget);
        deepSearch(fragmentRoot,widget, widget.getH());   //  深度优先遍历多叉树
    }

    private void deepSearch(AXmlNode node,Widget widget,int h){
        if (widget.getType().equals("fragment")){
            AXmlNode fragmentRoot = findFragment(widget);
            if (fragmentRoot!=null){
                Widget childWidget = new Widget(fragmentRoot);
                childWidget.setParent(widget);
                childWidget.setH(h+1);
                widget.addChildWidget(childWidget);
                deepSearch(fragmentRoot, childWidget,h+1); // fragment标签下没有子节点了，必须额外去寻找fragment的布局，将其根节点作为根；
            }

        } else{
            for (AXmlNode childNode : node.getChildren()){
                Widget childWidget = new Widget(childNode);
                childWidget.setParent(widget);
                childWidget.setH(h+1);
                widget.addChildWidget(childWidget);
                deepSearch(childNode,childWidget,h+1);

            }
        }
    }

    /** 静态添加的Fragment，找到其布局，获取布局根节点
     * @return*/
    private AXmlNode findFragment(Widget widget) {
        String fragmentClassName = widget.getAndroidName();
        if (fragmentClassName!=null){
            SootClass fragmentClass = Scene.v().getSootClassUnsafe(fragmentClassName);
            if (fragmentClass!=null){
                SootMethod onCreateView = fragmentClass.getMethodUnsafe("android.view.View onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)");
                if (onCreateView!=null){
                    Body body = onCreateView.retrieveActiveBody();
                    for (Unit unit : body.getUnits()){
                        Stmt stmt = (Stmt) unit;
                        if ( stmt instanceof InvokeStmt || stmt instanceof AssignStmt){
                            InvokeExpr invokeExpr = null;
                            SootMethod invokee = null;
                            if (stmt instanceof InvokeStmt){
                                invokeExpr = ((InvokeStmt) stmt).getInvokeExpr();
                                if (invokeExpr instanceof VirtualInvokeExpr)
                                    invokee = invokeExpr.getMethod();
                            }else{
                                if (((AssignStmt) stmt).containsInvokeExpr()){
                                    invokeExpr = ((AssignStmt) stmt).getInvokeExpr();
                                    if (invokeExpr instanceof VirtualInvokeExpr)
                                        invokee = invokeExpr.getMethod();
                                }
                            }

                            if (invokee!=null){
                                if (invokee.getName()=="inflate"){
                                    /** <android.view.LayoutInflater: android.view.View inflate(int,android.view.ViewGroup,boolean)>(2131427356, $r2, 0) */
                                    Value value = invokeExpr.getArg(0);
                                    if (value.getType().toString() == "int"){
                                        int layoutID = Integer.valueOf(value.toString());
                                            String layoutXmlName = AppParser.v().getLayoutNameById(layoutID);
                                            String layoutPath = "res/layout/" + layoutXmlName + ".xml";
                                            try {
                                                InputStream is = AppParser.v().getApkHandler().getInputStream(layoutPath);
                                                if (is != null) {
                                                    AXmlHandler aXmlHandler = new AXmlHandler(is);
                                                    AXmlDocument aXmlDocument = aXmlHandler.getDocument();
                                                    AXmlNode fragmentRoot = aXmlDocument.getRootNode();
                                                    return fragmentRoot;
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
        return null;
    }

    public void displayTree(Widget widget){
        Logger.i("["+widget.getH()+"]","\t"+widget.getType()+"\tid = "+widget.getAndroidId());
//        widget.displayWidget();
        for (Widget child : widget.getChildren()){
            displayTree(child);
        }
    }

    public String getActivityName() {
        return activity.getName();
    }

    public Widget getRootWidget() {
        return rootWidget;
    }
}
