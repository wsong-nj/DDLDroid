package tool.utils;

import soot.SootClass;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class ExcludePackage {
	private static final String TAG = "[Exclude]";
	
	private static ExcludePackage instance;
	public static ExcludePackage v() {
		if(instance == null) {
			synchronized (ExcludePackage.class) {
				if(instance == null) {
					instance = new ExcludePackage();
				}
			}
		}
		return instance;
	}


	private Set<String> excludesInFile = new HashSet<String>();
	private String pkgName = "";
	public Set<String> excludes = new HashSet<String>();


	private ExcludePackage() {
		// 首次调用时，加载所有，然后一直不用更改
		File excludefile = new File("./ExcludePackage.txt");
		if(excludefile.exists() && excludefile.isFile()) {
			//Logger.i(TAG, "find ExcludePackage.txt successfully");
			try {
				InputStreamReader is = new InputStreamReader(new FileInputStream(excludefile));
				BufferedReader reader = new BufferedReader(is);
				String line = null;
				while((line = reader.readLine()) != null) {
					if(!line.equals("")) {
						excludesInFile.add(line);
					}
				}
				reader.close();
			} catch (FileNotFoundException e) {
				Logger.e(TAG, e);
			} catch (IOException e) {
				Logger.e(TAG, e);
			}
		}else
			Logger.i(TAG, "No \"ExcludePackage.txt\" file found at "+excludefile.getAbsolutePath());
	}

//

	/** 更新包名，更新excludes列表 */
	public void setPkgName(String pkgName) {
		this.pkgName = pkgName;
		//每次调用此API，是一个新的APP在分析，更新excludes
		excludes = new HashSet<>();
		for (String s : excludesInFile){
			if ( this.pkgName.startsWith(s.substring(0,s.indexOf("*"))) ){
				continue;
			}
			excludes.add(s);
		}
	}

	public boolean isExclude(SootClass sc) {
		for(String s : excludes) {
			if(sc.getName().startsWith(s.substring(0,s.indexOf("*"))))
				return true;
		}
		return false;
	}


//	public static void main(String[] args) {
//		ExcludePackage.v().isExclude(Scene.v().getClasses().getFirst());
//	}
}
