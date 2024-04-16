package tool.utils;

import soot.SootClass;
import soot.SootMethod;

public class MethodService {

	/** 尝试解析代码体。因为存在部分method暂时没有active body，通过retrieve之后就有了
	 * @return 可以成功解析就返回true；无法解析就捕获异常进行处理，返回false */
	public static boolean tryRetrieveActiveBody(SootMethod sootMethod){
		try{
			if (sootMethod != null){
				sootMethod.retrieveActiveBody();
			}

		}catch (RuntimeException e){
			System.out.println("无法解析 "+sootMethod.getSignature()+" 的代码体");
			return false;
		}
		return true;
	}

	public static boolean isFragmentTransaction(SootMethod sm) {
		String cName = sm.getDeclaringClass().getName();
		if(cName.equals("android.app.FragmentTransaction") 
				|| cName.equals("android.support.v4.app.FragmentTransaction")
				|| cName.equals("androidx.fragment.app.FragmentTransaction")) {
			String mName = sm.getName();
			if(mName.equals("replace") || mName.equals("add")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isStartActivity(SootMethod sm) {
		String subSignature = sm.getSubSignature();
		if(subSignature.equals("void startActivity(android.content.Intent)")
				|| subSignature.equals("void startActivity(android.content.Intent,android.os.Bundle)")
				|| subSignature.equals("void startActivityForResult(android.content.Intent,int)")
				|| subSignature.equals("void startActivityForResult(android.content.Intent,int,android.os.Bundle)")
				|| subSignature.equals("void startActivityIfNeeded(android.content.Intent,int,android.os.Bundle)")
				|| subSignature.equals("void startActivityIfNeeded(android.content.Intent,int)")) {
			return true;
		}
		return false;
	}

	public static boolean isDialogFragmentShow(SootMethod sm) {
		String subSignature = sm.getSubSignature();
		if(subSignature.equals("void show(androidx.fragment.app.FragmentManager,java.lang.String)") 
				|| subSignature.equals("void show(android.support.v4.app.FragmentManager,java.lang.String)")
				|| subSignature.equals("int show(androidx.fragment.app.FragmentTransaction,java.lang.String)")
				|| subSignature.equals("int show(android.support.v4.app.FragmentTransaction,java.lang.String)")
				|| subSignature.equals("void showNow(androidx.fragment.app.FragmentManager,java.lang.String)")
				|| subSignature.equals("void showNow(android.support.v4.app.FragmentManager,java.lang.String)")
				|| subSignature.equals("void show()")) {
			return true;
		}
		return false;
	}
	
	public static boolean isAlertDialogShow(SootMethod sm) {
		String mName = sm.getName();
		SootClass sc = sm.getDeclaringClass();
		if((ClassService.isAlertDialog(sc) 
				|| ClassService.isAlertDialogBuilder(sc))
				&& mName.equals("show")) {
			return true;
		}
		return false;
	}
	
	public static boolean isDialogShow(SootMethod sm) {
		String mName = sm.getName();
		SootClass sc = sm.getDeclaringClass();
		if(sc.getName().equals("android.app.Dialog")
				&& mName.equals("show")) {
			return true;
		}
		return false;
	}

	public static boolean isViewCallbackRegister(SootMethod sm) {
		String mName = sm.getName();
		if(mName.equals("setOnClickListener")
				|| mName.equals("setOnItemClickListener")
				|| mName.equals("setOnLongClickListener")
				|| mName.equals("setOnItemLongClickListener")
				|| mName.equals("setOnScrollListener")
				|| mName.equals("setOnDragListener")
				|| mName.equals("setOnHoverListener")
				|| mName.equals("setOnTouchListener")) {
			return true;
		}
		return false;
	}

	public static boolean isPopupMenuShow(SootMethod sm) {
		String mSignature = sm.getSignature();
		if(mSignature.equals("<android.widget.PopupMenu: void show()>")
				|| mSignature.equals("<android.support.v7.widget.PopupMenu: void show()>")
				|| mSignature.equals("<androidx.appcompat.widget.PopupMenu: void show()>")) {
			return true;
		}
		return false;
	}
	
	public static boolean isGetStringOrText(SootMethod sm) {
		if(sm.getName().equals("getString") || sm.getName().equals("getText")) {
			SootClass sc = sm.getDeclaringClass();
			if(ClassService.isContext(sc))
				return true;
		}
		return false;
	}
}
