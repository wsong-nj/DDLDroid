package tool.analysisForTwoCase;

import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataVariable {

    private Type widgetType;

    /** 所在方法的触发路径; */
    /* 对于A恢复集合，path为activityName -> A() -> B();再加stmt。
     * 对于B保存集合，path为activityName -> A() -> setOnListener : setStmt -> listenerClassName #此处开始由handleListener()继续添加路径#-> onClick() -> B();再加stmt
     *      特例是菜单，path为activityName -> onOptionsItemSelected() [item case:menuItem] #此处开始由handleListener()继续添加路径#-> A() -> B();再加stmt*/
    private String path;
    private Stmt stmt;

    private Feature feature;

    /** sourcesink定义是一个list，因为比如dialog控件，通过.show()方法识别到，但它可能涉及关闭和打开两种状态均需要检测。
     * 例如： onShow监听中    需要有标记变量置为true的操作，并有保存数据流；
     *       onDismiss监听中 需要有标记变量置为true的操作，并有保存数据流；*/
    private List<ISourceSinkDefinition> sourcesink;   // 可用equals来判断
    private ControlDependency dependency;

    // 对应一个data flow变量，既可以表示save，又可以表示restore，取决于该对象在哪个集合中。

    public DataVariable(){
        this.widgetType = null;
        this.path = null;
        this.stmt = null;
        this.feature = null;
        this.sourcesink = null;
        this.dependency = null;
    }

    public static boolean isSame(DataVariable a, DataVariable b){
        if ( !a.getWidgetType().equals( b.getWidgetType() ))
            return false;
        if ( !a.getFeature().equals(b.getFeature()))
            return false;
        return true;
    }

    /** 如果添加的是null，则不添加，保证当soourcesink定义的list非空时，里面都是有效元素 */
    public void addSourceSink(ISourceSinkDefinition sourceSinkDefinition) {
        if (sourceSinkDefinition != null){
            if (sourcesink == null)
                sourcesink = new ArrayList<>();
            sourcesink.add(sourceSinkDefinition);
        }

    }


    public Type getWidgetType() {
        return widgetType;
    }

    public void setWidgetType(Type widgetType) {
        this.widgetType = widgetType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Stmt getStmt() {
        return stmt;
    }

    public void setStmt(Stmt stmt) {
        this.stmt = stmt;
    }

    public Feature getFeature() {
        return feature;
    }

    public void setFeature(Feature feature) {
        this.feature = feature;
    }
    /** 一定是有意义的定义。因为null等等都不会被加入而创建list，使得返回值为null*/
    public List<ISourceSinkDefinition> getSourcesink() {
        return sourcesink;
    }

    public void setSourcesink(List<ISourceSinkDefinition> sourcesink) {
        this.sourcesink = sourcesink;
    }

    public ControlDependency getDependency() {
        return dependency;
    }

    public void setDependency(ControlDependency dependency) {
        this.dependency = dependency;
    }
}
