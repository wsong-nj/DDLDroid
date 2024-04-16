# Introduction of DDLDroid

#### General information

DDLDroid is a static analyzer for detecting data loss issues in Android apps during activity restart or app relaunch. It is bootstrapped by a saving-restoring bipartite graph which correlates variables that need saving to those that need restoring according to their carrier widgets, and is based on the data flow analysis of variables saving and restoring operations. It reports data loss issues once missed or broken data flows are identified.

#### Implementation

- Based on a set of available tools (e.g., Soot, FlowDroid, ApkTool), DDLDroid is implemented in Java and has three analyzers: pretreatment analyzer, static analyzer, and data loss reporter. 

- The pretreatment analyzer is responsible for obtaining the Jimple code, the call graph, and the resource files from the APK file. Then, the activity/fragment classes and the statically registered methods that handles user events can be obtained for subsequent analysis.

- The static analyzer is responsible for the saving-restoring bipartite graph construction and data flow analysis. The saving-restoring bipartite graph correlates variables that need saving to those that need restoring according to their carrier widgets, and then, data flow analysis generates the data flows of saving and restoring for theses variables with taint analysis.

- The data loss reporter reveals the found data loss issues based on the saving-restoring bipartite graph and the data flows of restoring and saving. The results are outputted to a csv file.

# Introduction of Artifact Repository  

This repository contains the DDLDroid tool, the dataset and the evaluation results.

- The "Comparison Results" folder contains all the results of data loss issues that are detected by the comparison tools (LiveDroid, DLD, and iFixDataloss).

- The "DDLDroid" folder contains the source code of DDLDroid.

- The "Results_details" folder contains the dataset (the 66 APK files) and all the results of data loss issues detected by DDLDroid. Since we confirm all the results manually, each xlsx file named by the app in this folder contains the corresponding detected results and the green cells in the table means that they have been confirmed by us. Moreover, the key steps of reproducing the confirmed issues are also contained in these xlsx files. Notably,  although DDLDroid only outputs the detected results to a csv file for each app originally, the results here is saved in xlsx files because we want to mark these green cells in each table.

- The "Screenshots" folder contains all the screenshots for the confirmed data loss issues.

- The "MyResults.xlsx" file summarizes all the detected results by DDLDroid and the comparison tools. "#T" means the number of all detected data loss issues;  "#TP" means the number of true positives; "#FP" means the number of false positives; "#UC" means the number of the detected data loss issues that have not been confirmed manually by us yet;

# Getting Started with DDLDroid

#### The Artifact's Requirements (Environment info)
- Windows 10
- JDK 8
- Android SDK (version 10)
- Intellij IDEA installation
- Android device (or emulator), if you want to reproduce the data loss issues manually

#### Basic Functionality of DDLDroid

- Input: The APK files of Android apps.

- Output: A table (cvs file) that lists all detected data loss issues for each app.

- Quick Run (The steps to check the basic functionality of the artifact): To get started, we have put an APK file to the default folder "./resource/testApk" in the project of DDLDroid. After importing DDLDroid project into IDEA, please modify the parameter *androidJAR* in "./resource/config.properties" to your own, you can run "./src/tool/guiForAnalysis/GUIForAnalysisStart.java". Then, choose to detect data loss issues, and the detected results will be generated in "./resource/testApk" as well. The time cost of the quick run is a few seconds. If DDLDroid does not work, please follow the detailed instructions to check the dependence jars.

#### Data Loss Issue Reproduction

The data loss issues that have been manually confirmed by us in this dataset can be easily reproduced on an Android device, please:

- Install the app.
- In the "Results_details" folder, open the corresponding xlsx file for the app and focus on the detected issues that have been confirmed by us (the green cells in the table).
- Follow the provided  key steps at the last row of each item that is in green cells, then click the corresponding views on the screen to guide the app to the specific state .
- Simulate the activity restart or app relaunch events to reproduce the data loss issue. For example, screen rotation and system theme switch (two typical configuration changes) can be used to trigger activity restart. As to app relaunch, the setting of Background process limit in the developer options can be set to “no background processes”. Consequently, the Android system will destroy the process of the tested app when we navigate to another app. When we navigate back to the tested app, the app relaunch is simulated successfully.

# Detailed Instructions

#### Apply DDLDroid to Other Apps

- Import the project and dependence jars: After , please: 

  1.Open the Intellij IDEA

  2.Click File;  

  3.Click Open..., and then select the project of DDLDroid; 

  4.Import dependence jars (File - Project structure - Project Settings - Modules - Dependencies, click the sign "+" to add all the jar files in the lib folder of DDLDroid).

- Modify configurations to your own: 

  Please open the configuration file "./resource/config.properties". There exist three parameters in the configuration file. Notably, the parameter *androidJAR* must be modified to your own. Additionally, if you want to run DDLDroid on the other apps rather than the app BeeCount, the two parameters, *apkFileDirectory* and *JimpleOutputDir* need to be modified to your own as well. You do not need to set the app (e.g., installation, permission settings) because DDLDroid takes the apk file that are in *apkFileDirectory* as its input.

- Run analysis: Run "./src/tool/guiForAnalysis/GUIForAnalysisStart.java", and then you can choose to obtain the Jimple IR or detect data loss issues. If you choose to detect data loss issues, DDLDroid outputs the detection results to the same directory where the APK file is located, and the file storing the detection results (result.csv) is generated in the folder named by the app (APK name). If you choose to obtain the Jimple code, DDLDroid outputs the Jimple code to the *JimpleOutputDir* folder you set. For each app, time cost is usually a few seconds, and the longest time cost is around 5 minutes.

- Read the detected resluts in the csv file: Every detected data loss issue are listed in the csv file. For each item of the reported data loss issues, it has a "Feature" description that shows the details (e.g., type, location, widget variable) of the widget affected by data loss issue. Then, the item shows the paths how DDLDroid identifies the variables that need saving and restoring.
