package tool.analysisForTwoCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.source.ConfigurationBasedCategoryFilter;
import soot.jimple.infoflow.android.source.parsers.xml.XMLSourceSinkParser;
import soot.jimple.infoflow.sourcesSinks.definitions.*;
import tool.basicAnalysis.AppParser;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestoreFlowSourceSinkDefinitionProvider implements ISourceSinkDefinitionProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, AndroidMethod> methods = null;
    private Set<ISourceSinkDefinition> SourceList = null;
    private Set<ISourceSinkDefinition> SinkList = null;
    private Set<ISourceSinkDefinition> NeitherList = null;

    private List<String> data;
    private final String regex = "^<(.+):\\s*(.+)\\s+(.+)\\s*\\((.*)\\)>\\s*(.*?)(\\s+->\\s+(.*))?$";
    private final String regexNoRet = "^<(.+):\\s*(.+)\\s*\\((.*)\\)>\\s*(.*?)?(\\s+->\\s+(.*))?$";


    public static RestoreFlowSourceSinkDefinitionProvider newRestoreFlow(String sinkFileName) throws IOException {
        RestoreFlowSourceSinkDefinitionProvider sourcesAndSinks = new RestoreFlowSourceSinkDefinitionProvider();
        sourcesAndSinks.readFile(sinkFileName); // 这一步仅载入存在txt中的Source点
        sourcesAndSinks.readTempFile("./temp.txt");
        return sourcesAndSinks;
    }

    public RestoreFlowSourceSinkDefinitionProvider() {
    }
    /**补充定义xml形式的source，用于构造关于恢复的读取数据的数据流*/
    public void handleSpecialAPIsFromXML(InfoflowAndroidConfiguration config) throws IOException {
        ISourceSinkDefinitionProvider tempFromXml = XMLSourceSinkParser.fromFile("./parts.xml",
                new ConfigurationBasedCategoryFilter(config.getSourceSinkConfig()));
        for(ISourceSinkDefinition t : tempFromXml.getSources()){
            addSource(t);
        }
    }

    private void readTempFile(String s) throws IOException {
        FileReader fr = null;
        try {
            File temp = new File(s);
            if (temp.exists()){
                fr = new FileReader(s);
                String line;
                BufferedReader br = new BufferedReader(fr);
                try {
                    while ((line = br.readLine()) != null)
                        this.data.add(line);
                } finally {
                    br.close();
                }
            }

        } finally {
            if (fr != null)
                fr.close();
        }
    }

    private void readFile(String sinkFileName) throws IOException {
        FileReader fr = null;
        try {
            fr = new FileReader(sinkFileName);
            readReader(fr);
        } finally {
            if (fr != null)
                fr.close();
        }
    }

    private void readReader(Reader r) throws IOException {
        String line;
        this.data = new ArrayList<String>();
        BufferedReader br = new BufferedReader(r);
        try {
            while ((line = br.readLine()) != null)
                this.data.add(line);
        } finally {
            br.close();
        }

    }

    /**
     * 向恢复数据流检测中添加自定义的 statement-based Sink*/
    public void addSink(ISourceSinkDefinition sink){
        if (SourceList==null || SinkList==null)
            parse();
        this.SinkList.add(sink);
    }

    public void addSource(ISourceSinkDefinition source) {
        if (SourceList==null || SinkList==null)
            parse();
        this.SourceList.add(source);
    }

    @Override
    public Set<ISourceSinkDefinition> getSources() {
        if (SourceList==null || SinkList==null)
            parse();
        return this.SourceList; // 一定会由parse()进行初始化，所以不会返回null
    }

    @Override
    public Set<ISourceSinkDefinition> getSinks() {
        if (SourceList==null || SinkList==null)
            parse();
        return this.SinkList;   // 一定会由parse()进行初始化，所以不会返回null
    }

    private void parse() {
        // 从整理好的txt中收集Source，以赋值语句作为Sink;
        this.methods = new HashMap<>();
        this.SourceList = new HashSet<>();
        this.SinkList = new HashSet<>();
        this.NeitherList = new HashSet<>();

        // 创建Sources
        Pattern p = Pattern.compile(regex);
        Pattern pNoRet = Pattern.compile(regexNoRet);

        for (String line : this.data) {
            if (line.isEmpty() || line.startsWith("%"))
                continue;
            Matcher m = p.matcher(line);
            if (m.find()) {
                this.createMethod(m);
            } else {
                Matcher mNoRet = pNoRet.matcher(line);
                if (mNoRet.find()) {
                    createMethod(mNoRet);
                } else
                    logger.warn(String.format("Line does not match: %s", line));
            }
        }

        // Create the source definitions
        for (AndroidMethod am : methods.values()) {
            MethodSourceSinkDefinition singleMethod = new MethodSourceSinkDefinition(am);

            if (am.getSourceSinkType().isSource())
                SourceList.add(singleMethod);
//            if (am.getSourceSinkType().isSink())
//                SinkList.add(singleMethod);
//            if (am.getSourceSinkType() == SourceSinkType.Neither)
//                NeitherList.add(singleMethod);
        }



    }

    private AndroidMethod createMethod(Matcher m) {
        AndroidMethod am = parseMethod(m, true);
        AndroidMethod oldMethod = methods.get(am.getSignature());
        if (oldMethod != null) {
            oldMethod.setSourceSinkType(oldMethod.getSourceSinkType().addType(am.getSourceSinkType()));
            return oldMethod;
        } else {
            methods.put(am.getSignature(), am);
            return am;
        }
    }

    private AndroidMethod parseMethod(Matcher m, boolean hasReturnType) {
        assert (m.group(1) != null && m.group(2) != null && m.group(3) != null && m.group(4) != null);
        AndroidMethod singleMethod;
        int groupIdx = 1;

        // class name
        String className = m.group(groupIdx++).trim();

        String returnType = "";
        if (hasReturnType) {
            // return type
            returnType = m.group(groupIdx++).trim();
        }

        // method name
        String methodName = m.group(groupIdx++).trim();

        // method parameter
        List<String> methodParameters = new ArrayList<String>();
        String params = m.group(groupIdx++).trim();
        if (!params.isEmpty())
            for (String parameter : params.split(","))
                methodParameters.add(parameter.trim());

        // permissions
        String classData = "";
        String permData = "";
        Set<String> permissions = null;
        ;
        if (groupIdx < m.groupCount() && m.group(groupIdx) != null) {
            permData = m.group(groupIdx);
            if (permData.contains("->")) {
                classData = permData.replace("->", "").trim();
                permData = "";
            }
            groupIdx++;
        }
        if (!permData.isEmpty()) {
            permissions = new HashSet<String>();
            for (String permission : permData.split(" "))
                permissions.add(permission);
        }

        // create method signature
        singleMethod = new AndroidMethod(methodName, methodParameters, returnType, className, permissions);

        if (classData.isEmpty())
            if (m.group(groupIdx) != null) {
                classData = m.group(groupIdx).replace("->", "").trim();
                groupIdx++;
            }
        if (!classData.isEmpty())
            for (String target : classData.split("\\s")) {
                target = target.trim();

                // Throw away categories
                if (target.contains("|"))
                    target = target.substring(target.indexOf('|'));

                if (!target.isEmpty() && !target.startsWith("|")) {
                    if (target.equals("_SOURCE_"))
                        singleMethod.setSourceSinkType(SourceSinkType.Source);
                    else if (target.equals("_SINK_"))
                        singleMethod.setSourceSinkType(SourceSinkType.Sink);
                    else if (target.equals("_NONE_"))
                        singleMethod.setSourceSinkType(SourceSinkType.Neither);
                    else if (target.equals("_BOTH_"))
                        singleMethod.setSourceSinkType(SourceSinkType.Both);
                    else
                        throw new RuntimeException("error in target definition: " + target);
                }
            }
        return singleMethod;
    }

    @Override
    public Set<ISourceSinkDefinition> getAllMethods() {
        if (SourceList==null || SinkList==null)
            parse();

        Set<ISourceSinkDefinition> sourcesSinks = new HashSet<>(
                SourceList.size() + SinkList.size() + NeitherList.size());
        sourcesSinks.addAll(SourceList);
        sourcesSinks.addAll(SinkList);
        sourcesSinks.addAll(NeitherList);

        return sourcesSinks;
    }

}
