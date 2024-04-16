package tool.guiForAnalysis;

import tool.entryForAllApks.EntryForAll;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;

public class GUIForAnalysisStart {

    public static void main(String[] args) {
        String androidJAR="";
        String apkFileDirectory="";
        String JimpleOutputDir="";
        Properties prop = new Properties();
        try {
            prop.load(new BufferedInputStream(new FileInputStream("./resource/config.properties")));
            androidJAR = prop.getProperty("androidJAR");
            apkFileDirectory = prop.getProperty("apkFileDirectory");
            JimpleOutputDir = prop.getProperty("JimpleOutputDir") + "//";
            System.out.println(androidJAR);
            System.out.println(apkFileDirectory);
            System.out.println(JimpleOutputDir);
            //return;
        } catch (IOException e) {
            System.out.println("config.properties read error! Please contact the developers.");
            e.printStackTrace();
        }
//        String androidJAR = "D:\\Android\\AndroidSDK\\platforms"; //暂时不考虑\android-29\android.jar，后面已统一    \android-29\android.jarn
//        String apkFileDirectory = "C:\\Users\\Dell\\Desktop\\apks2"; // D:\AppsForFDLDroidTest\APK\新建文件夹  D:\AppsForFDLDroidTest\APK\小规模
//        String JimpleOutputDir = "C:\\Users\\Dell\\Desktop\\apks2\\Jimple_Code\\";   // + appName;    D:\JimpleCodesLocation\

        boolean isOutputJimple = false; // whether to output jimples. true value can get Jimple Codes; false value can do analysis;

        System.out.println("Output Jimple code mode?  (Y/N)");
        System.out.println("if you select this mode, DDLDroid only generates the IR at the directory you set and does not detect data loss.");
        Scanner sc = new Scanner(System.in);
        String selection = sc.nextLine();
        if (selection.equals("y") || selection.equals("Y"))
            isOutputJimple = true;

        //String resultPath = "./test";
        String[] locations = { apkFileDirectory, androidJAR, JimpleOutputDir };
        EntryForAll entryForAll = new EntryForAll(locations, isOutputJimple);
        ArrayList<String> allApksPathList = entryForAll.getAllApkFilespath();

        try {
            entryForAll.analyzeAll(allApksPathList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
