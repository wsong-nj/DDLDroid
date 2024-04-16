package tool.utils;

import soot.SootClass;
import tool.basicAnalysis.AppParser;

public class ClassService {

	public static boolean isActivity(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.app.Activity")
				|| sc.getName().equals("android.support.v7.app.AppCompatActivity")
				|| sc.getName().equals("androidx.appcompat.app.AppCompatActivity"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			if(superCls.getName().equals("android.app.Activity")
					|| superCls.getName().equals("android.support.v7.app.AppCompatActivity")
					|| superCls.getName().equals("androidx.appcompat.app.AppCompatActivity")) {
				//androidx.appcompat.app.AppCompatActivity
				//android.support.v7.app.AppCompatActivity
				//android.app.Activity
				return true;
			}
		}
		return false;
	}

	public static boolean isFragment(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.app.Fragment")
				|| sc.getName().equals("androidx.fragment.app.Fragment")
				|| sc.getName().equals("android.support.v4.app.Fragment"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.Fragment")
					|| superClsName.equals("androidx.fragment.app.Fragment")
					|| superClsName.equals("android.support.v4.app.Fragment")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isPrefsUI(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.preference.PreferenceFragment"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.preference.PreferenceFragment")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isPrefsUISupportV7(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.support.v7.preference.PreferenceFragmentCompat"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.support.v7.preference.PreferenceFragmentCompat")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isPrefsDialog(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.support.v7.preference.DialogPreference")
				||sc.getName().equals("android.preference.DialogPreference"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.support.v7.preference.DialogPreference")
					||superClsName.equals("android.preference.DialogPreference")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isTextView(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.widget.TextView"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.widget.TextView")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isEditText(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.widget.EditText"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.widget.EditText")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDrawerLayout(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("androidx.drawerlayout.widget.DrawerLayout"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("androidx.drawerlayout.widget.DrawerLayout")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDialog(SootClass sc) {
		SootClass superCls = sc;
		if (sc.getName().equals("android.app.Dialog"))
			return true;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.Dialog")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDialogBuilder(SootClass sc) {
		String clsName = sc.getName();
		if(clsName.equals("android.app.Dialog$Builder")) {
			return true;
		}
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.Dialog$Builder")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAlertDialog(SootClass sc) {
		String clsName = sc.getName();
		if(clsName.equals("android.app.AlertDialog")
				|| clsName.equals("android.support.v7.app.AlertDialog")
				|| clsName.equals("androidx.appcompat.app.AlertDialog")) {
			return true;
		}
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.AlertDialog")
					|| superClsName.equals("android.support.v7.app.AlertDialog")
					|| superClsName.equals("androidx.appcompat.app.AlertDialog")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAlertDialog2(SootClass sc) {
		SootClass superCls = sc;
		if(superCls.getName().equals("android.app.AlertDialog")
				|| superCls.getName().equals("android.support.v7.app.AlertDialog")
				|| superCls.getName().equals("androidx.appcompat.app.AlertDialog")) {
			return true;
		}
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.AlertDialog")
					|| superClsName.equals("android.support.v7.app.AlertDialog")
					|| superClsName.equals("androidx.appcompat.app.AlertDialog")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAlertDialogBuilder(SootClass sc) {
		String clsName = sc.getName();
		if(clsName.equals("android.app.AlertDialog$Builder")
				|| clsName.equals("android.support.v7.app.AlertDialog$Builder")
				|| clsName.equals("androidx.appcompat.app.AlertDialog$Builder")) {
			return true;
		}
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.app.AlertDialog$Builder")
					|| superClsName.equals("android.support.v7.app.AlertDialog$Builder")
					|| superClsName.equals("androidx.appcompat.app.AlertDialog$Builder")) {
				return true;
			}
		}
		return false;
	}

	// 用户自定义包装的builder
	public static boolean isWrapperAlertDialogBuilder(SootClass sc) {
		String clsName = sc.getName();
		String pkgName = sc.getPackageName();
		if(clsName.contains("DialogBuilder") && pkgName.contains(AppParser.v().getPkg())) {
			return true;
		}
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superPkgName = sc.getPackageName();
			String superClsName = superCls.getName();
			if(superClsName.contains("DialogBuilder") && superPkgName.contains(AppParser.v().getPkg())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDialogFragment(SootClass sc) {
		SootClass superCls = sc;	//
		if(superCls.getName().equals("android.support.v4.app.DialogFragment")
				|| superCls.getName().equals("android.app.DialogFragment")) {
			return true;
		}
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("android.support.v4.app.DialogFragment")
					|| superCls.getName().equals("android.app.DialogFragment")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isView(SootClass sc) {
		if(sc.getName().equals("android.view.View")) {
			return true;
		}else {
			SootClass superCls = sc;
			while(superCls.hasSuperclass()) {
				superCls = superCls.getSuperclass();
				if(superCls.getName().equals("android.view.View")) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isViewGroup(SootClass sc) {
		if(sc.getName().equals("android.view.ViewGroup")) {
			return true;
		}else {
			SootClass superCls = sc;
			while(superCls.hasSuperclass()) {
				superCls = superCls.getSuperclass();
				if(superCls.getName().equals("android.view.ViewGroup")) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isViewModel(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("androidx.lifecycle.AndroidViewModel")
					|| superClsName.equals("androidx.lifecycle.ViewModel")) {
				return true;
			}
		}
		return false;
	}


	public static boolean isContext(SootClass sc) {
		String cName = sc.getName();
		if(cName.equals("android.content.Context")) {
			return true;
		}else {
			SootClass superCls = sc;
			while(superCls.hasSuperclass()) {
				superCls = superCls.getSuperclass();
				if(superCls.getName().equals("android.content.Context")) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isImageView(SootClass sc) {
		SootClass superCls = sc;
		if(superCls.getName().equals("android.widget.ImageView")) {
			return true;
		}
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			if(superCls.getName().equals("android.widget.ImageView")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDialogInterfaceListener(SootClass sc) {
		
		return false;
	}

	public static boolean isAsyncTask(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			if(superCls.getName().equals("android.os.AsyncTask"))
				return true;
		}
		return false;
	}
	
	public static boolean isRunnable(SootClass sc) {
		SootClass superCls = sc;
		for(SootClass inter : superCls.getInterfaces()) {
			if(inter.getName().equals("java.lang.Runnable")) {
				return true;
			}
		}
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			for(SootClass inter : superCls.getInterfaces()) {
				if(inter.getName().equals("java.lang.Runnable")) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static boolean isCustomThread(SootClass sc) {
		if(sc.getName().equals("java.lang.Thread"))
			return false;
		else {
			SootClass superCls = sc;
			while(superCls.hasSuperclass()) {
				superCls = superCls.getSuperclass();
				if(superCls.getName().equals("java.lang.Thread"))
					return true;
			}
		}
		return false;
	}

	public static boolean isAdapterView(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			if(superCls.getName().equals("android.widget.AdapterView")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAdapter(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			if(superCls.getName().equals("android.widget.Adapter")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRecyclerView(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("androidx.recyclerview.widget.RecyclerView")
					|| superClsName.equals("android.support.v7.widget.RecyclerView")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRecyclerViewAdapter(SootClass sc) {
		SootClass superCls = sc;
		while(superCls.hasSuperclass()) {
			superCls = superCls.getSuperclass();
			String superClsName = superCls.getName();
			if(superClsName.equals("androidx.recyclerview.widget.RecyclerView$Adapter")
					|| superClsName.equals("android.support.v7.widget.RecyclerView$Adapter")) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isPopupMenu(SootClass sc) {
		String cName = sc.getName();
		if(cName.equals("android.widget.PopupMenu")
				|| cName.equals("android.support.v7.widget.PopupMenu")
				|| cName.equals("androidx.appcompat.widget.PopupMenu")) {
			return true;
		}
		return false;
	}



}
