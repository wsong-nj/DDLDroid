package tool.utils;

import java.io.*;
import java.util.Map;

public class IOService {
	private static final String TAG = "[IOService]";
	
	public static void reset() {
		instance = null;
	}
	
	private static IOService instance = null;

	private IOService() {}

	public static IOService v() {
		if(instance == null) {
			synchronized (IOService.class) {
				if(instance == null) {
					instance = new IOService();
				}
			}
		}
		return instance;
	}


	private File outputDir;			// app名的根目录文件夹
	private File decompileFile;		// decompile名的一级子目录
	private File staticResultFile;	// static名的一级子目录

	private File pkgFile;


	/** 初始化分析结果路径为apk同文件夹下的同名文件夹内 */
	public void init(String apkDir, String apkName) {
		outputDir = new File(apkDir + apkName);	// apk文件 同级的文件夹下 的 app同名文件夹
		if(!outputDir.exists() || !outputDir.isDirectory()) {
			outputDir.mkdirs();
		}

		decompileFile = new File(outputDir, "decompile");
		if(!decompileFile.exists() || !decompileFile.isDirectory()) {
			decompileFile.mkdirs();
		}

		staticResultFile = new File(outputDir, "static");
		if(!staticResultFile.exists() || !staticResultFile.isDirectory()) {
			staticResultFile.mkdirs();
		}
		pkgFile = new File(staticResultFile, "Package.txt");

	}

	
	private void writer(File file, String text, boolean append) throws IOException {
		if(!file.exists() || !file.isFile()){
			file.createNewFile();
		}
		else{
			if (file.delete()){
				//System.out.println("旧pkgName文件删除成功");
			}

		}


		try(FileOutputStream fileOutputStream = new FileOutputStream(file, append);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)){
			bufferedWriter.append(text).append("\r\n");
		}
	}
	
	public void writePkg(String pkg) {
		try {
			writer(pkgFile, pkg, false);
		} catch (IOException e) {
			Logger.e(TAG, e);
		}
	}

	
	public void writeResources(Map<String, String> wId_wName, Map<String, String> lId_lName, Map<String, String> xId_xName, Map<String, String> mId_mName, Map<String, String> nId_nName) {
		File resFile = new File(staticResultFile, "Resources.csv");
		if (resFile.exists()){
			if (resFile.delete()){
				//System.out.println("旧Resources.csv删除成功");
			}

		}

		try(FileOutputStream out = new FileOutputStream(resFile);
			OutputStreamWriter osw = new OutputStreamWriter(out);
			BufferedWriter bw = new BufferedWriter(osw)){
			
			bw.write("Id,Name\n");
			bw.write("1,Widget\n");
			writeMap(wId_wName, bw);
			bw.write("2,LayoutFileName\n");
			writeMap(lId_lName, bw);
			bw.write("3,XmlFileName\n");
			writeMap(xId_xName, bw);
			bw.write("4,MenuFileName\n");
			writeMap(mId_mName, bw);
			bw.write("5,NavigationFileName\n");
			writeMap(nId_nName, bw);

		} catch (FileNotFoundException e) {
			Logger.e(TAG, new RuntimeException("write resources csv fail"));
		} catch (IOException e) {
			Logger.e(TAG, new RuntimeException("write resources csv fail"));
		}
	}
	private void writeMap(Map<String, String> map, BufferedWriter bw) throws IOException {
		for(String s : map.keySet()) {
			bw.write(s + "," + map.get(s) + "\n");
		}
	}
	
	public void writeString(Map<String, String> sId_sName, Map<String, String> name_text) {
		File strings = new File(staticResultFile, "strings.csv");
		if (strings.exists()){
			if (strings.delete()){
				//System.out.println("旧strings.csv删除成功");
			}

		}
		try(FileOutputStream out = new FileOutputStream(strings);
			OutputStreamWriter osw = new OutputStreamWriter(out);
			BufferedWriter bw = new BufferedWriter(osw)){
			bw.write("size,"+sId_sName.size() + "," + name_text.size() + "\n");
			bw.write("Id,Name,Value\n");
			for(String id : sId_sName.keySet()) {
				String name = sId_sName.get(id);
				String value = name_text.get(name);
				bw.write(id + "," + name + "," + (value == null ? "" : value) + "\n");
			}

		} catch (FileNotFoundException e) {
			Logger.e(TAG, new RuntimeException("write strings.csv fail"));
		} catch (IOException e) {
			Logger.e(TAG, new RuntimeException("write strings.csv fail"));
		}
	}

}
