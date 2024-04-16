package tool.analysisForTwoCase;

import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.Stmt;

import java.util.*;

/** 封装settings语句们的类*/
public class WidgetSetting {

    private List<Stmt> settingsStmt;
    private Map< String,List<String> > settingsMap;

    public WidgetSetting(List<Stmt> settings) {
        this.settingsStmt = settings;
        settingsMap = getSettingMethodsAndArgs(this.settingsStmt);
    }

    public List<Stmt> getSettings() {
        return settingsStmt;
    }

    public void setSettings(List<Stmt> settings) {
        this.settingsStmt = settings;
    }

    public Map<String, List<String>> getSettingsMap() {
        return settingsMap;
    }

    public void setSettingsMap(Map<String, List<String>> settingsMap) {
        this.settingsMap = settingsMap;
    }

    /**通过dialog的属性设置API们，获取 这些api的方法，及其对应的所有参数的类型*/
    private static Map<String,List<String>> getSettingMethodsAndArgs(List<Stmt> stmts){
        Map< String,List<String> > settings = new HashMap<>();
        for (Stmt stmt : stmts){
            if (stmt.containsInvokeExpr()){
                SootMethod sootMethod = stmt.getInvokeExpr().getMethod();
                List<String> argTypes = new ArrayList<>();
                List<Value> args = stmt.getInvokeExpr().getArgs();

                for (Value arg : args){
                    String argType = arg.getType().toString();
                    if (arg instanceof Constant)
                        argType+= ":"+arg;
                    argTypes.add(argType);
                }
                settings.put(sootMethod.getName(),argTypes);
            }
        }
        return settings;
    }

    /**不严格比对属性设置语句，只需要保证用的API相同，参数类型相同就可以了*/
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        WidgetSetting widgetSetting1 = (WidgetSetting) o;
        if (getSettingsMap().equals(widgetSetting1.getSettingsMap())) // equals，判断对应的key和value相等
            return true;

        return false;
    }


    @Override
    public int hashCode() {
        return Objects.hash(getSettingsMap());
    }

    @Override
    public String toString() {
        return ""+settingsMap;
    }
}
