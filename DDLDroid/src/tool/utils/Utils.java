package tool.utils;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.ReturnStmt;
import soot.toolkits.graph.UnitGraph;
import tool.basicAnalysis.AppParser;

import java.util.List;

public class Utils {
	
	public static String ADAPTER = "ADAPTER";
	public static String OPENCONTEXTMENU = "OPENCONTEXTMENU";
	
	public static String getViewCallbackMethod(String s) {
		switch (s) {
		case "setOnClickListener":
            return "onClick";
		case "setOnItemClickListener":
            return "onItemClick";
		case "setOnLongClickListener":
            return "onLongClick";
		case "setOnItemLongClickListener":
            return "onItemLongClick";
		case "setOnScrollListener":
            return "onScroll";
		case "setOnDragListener":
            return "onDrag";
		case "setOnHoverListener":
            return "onHover";
		case "setOnTouchListener":
            return "onTouch";
		default:
			return null;
		}
    }

	
	public static ReturnStmt getReturnStmt(UnitGraph cfg) {
		List<Unit> tails = cfg.getTails();
		for(Unit tail : tails) {
			if(tail instanceof ReturnStmt) {
				return (ReturnStmt)tail;
			}
		}
		return null;
	}
	
	public static void getRelatedClasses(SootClass owner, List<SootClass> innerClasses, List<SootClass> subClasses, 
			List<SootClass> pkgClasses, List<SootClass> others, boolean isProtected) {
		System.out.println("[AAA] allclass : " + AppParser.v().getAllClasses().size());
		String ownerName = owner.getName();
		String pkgName = null;
		int index = ownerName.lastIndexOf(".");
		if(index != -1) 
			pkgName = ownerName.substring(0, index);
		else
			pkgName = owner.getJavaPackageName();
		for(SootClass sc : AppParser.v().getAllClasses()) {
			if(sc.getName().startsWith(ownerName + "$")) {
				innerClasses.add(sc);
			}else if(sc.getName().startsWith(pkgName)) {
				pkgClasses.add(sc);
			}else {
				if(isProtected) {
					boolean flag = false;
					SootClass superCls = sc;
					while(superCls.hasSuperclass()) {
						superCls = sc.getSuperclass();
						if(superCls.getName().equals(ownerName)) {
							subClasses.add(sc);
							flag = true;
							break;
						}
						if(superCls.getName().startsWith("android"))
							break;
					}
					if(!flag)
						others.add(sc);
				}
			}
		}
	}
	
	public static void getRelatedClasses(SootClass owner, List<SootClass> innerClasses, List<SootClass> subClasses, 
			List<SootClass> pkgClasses, List<SootClass> others, String FLAG) {
		System.out.println("[AAA] allclass : " + AppParser.v().getAllClasses().size());
		String ownerName = owner.getName();
		String pkgName = null;
		int index = ownerName.lastIndexOf(".");
		if(index != -1) 
			pkgName = ownerName.substring(0, index);
		else
			pkgName = owner.getJavaPackageName();
		for(SootClass sc : AppParser.v().getAllClasses()) {
			if(sc.getName().startsWith(ownerName + "$")) {
				innerClasses.add(sc);
			}else if(sc.getName().startsWith(pkgName)) {
				pkgClasses.add(sc);
			}else {
				if(FLAG.equals("protected")) {
					boolean flag = false;
					SootClass superCls = sc;
					while(superCls.hasSuperclass()) {
						superCls = sc.getSuperclass();
						if(superCls.getName().equals(ownerName)) {
							subClasses.add(sc);
							flag = true;
							break;
						}
						if(superCls.getName().startsWith("android"))
							break;
					}
					if(!flag)
						others.add(sc);
				}
			}
		}
	}
	
	public static boolean isClassInSystemPackage(String className) {

		return className.startsWith("android.")
				|| className.startsWith("java.")
				|| className.startsWith("sun.");
	}
}
