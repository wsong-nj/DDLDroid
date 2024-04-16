package tool.analysisForTwoCase;

import soot.SootField;
import soot.Type;
import soot.jimple.infoflow.android.axml.AXmlAttribute;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public class Feature {
    private String activityName;
    private Type widgetType;

    /** 诸如TextView一般对应于类中的一个域*/
    private LinkedList<SootField> widgetField;
    private WidgetSetting widgetSetting;

    private Map<String, AXmlAttribute<?>> prefsAttributes;

    public Feature(String activityName, Type widgetType, Map<String, AXmlAttribute<?>> attributes) {
        this.activityName = activityName;
        this.widgetType = widgetType;
        this.widgetField = null;
        this.widgetSetting = null;
        this.prefsAttributes = attributes;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public Feature(String activityName, Type widgetType, LinkedList<SootField> widgetField) {
        this.activityName = activityName;
        this.widgetType = widgetType;
        this.widgetField = widgetField;
        this.widgetSetting = null;
        this.prefsAttributes = null;
    }

    public Feature(String activityName, Type widgetType, WidgetSetting widgetSetting) {
        this.activityName = activityName;
        this.widgetType = widgetType;
        this.widgetField = null;
        this.widgetSetting = widgetSetting;
        this.prefsAttributes = null;
    }

    public Feature() {
    }

    public Type getWidgetType() {
        return widgetType;
    }

    public void setWidgetType(Type widgetType) {
        this.widgetType = widgetType;
    }

    public LinkedList<SootField> getWidgetField() {
        return widgetField;
    }

    public void setWidgetField(LinkedList<SootField> widgetField) {
        this.widgetField = widgetField;
    }

    public WidgetSetting getWidgetSetting() {
        return widgetSetting;
    }

    public void setWidgetSetting(WidgetSetting widgetSetting) {
        this.widgetSetting = widgetSetting;
    }

    public Map<String, AXmlAttribute<?>> getPrefsAttributes() {
        return prefsAttributes;
    }

    public void setPrefsAttributes(Map<String, AXmlAttribute<?>> prefsAttributes) {
        this.prefsAttributes = prefsAttributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Feature feature = (Feature) o;
        if((feature.getWidgetField()==null||feature.getWidgetField().size()==0) && (feature.getWidgetSetting()==null||feature.getWidgetSetting().getSettings().size()==0))
            return false;
        // 同activity、同控件类型、同特征
        boolean ans =  getActivityName().equals(feature.getActivityName())
                && getWidgetType().equals(feature.getWidgetType())
                && Objects.equals(getWidgetField(), feature.getWidgetField())
                && Objects.equals(getWidgetSetting(), feature.getWidgetSetting())
                && Objects.equals(getPrefsAttributes(), feature.getPrefsAttributes());

        return ans;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getActivityName(), getWidgetType(), getWidgetField(), getWidgetSetting(), getPrefsAttributes());
    }

    @Override
    public String toString() {
        if (widgetField!=null) {
            StringBuilder sbWidgetField = new StringBuilder();
            sbWidgetField.append(widgetField.get(0));
            if (widgetField.size()>1){
                for (int i = 1; i<widgetField.size(); i++) {
                    sbWidgetField.append(" =>> "+widgetField.get(i));
                }
            }
            return "Feature: { " +
                    "UI=" + activityName +
                    ",widgetType=" + widgetType +
                    ", widgetField= [ " + sbWidgetField +
                    " ] }";
        }
        else if(widgetSetting!=null)
            return "Feature: {" +
                    "UI=" + activityName +
                    ",widgetType=" + widgetType +
                    ", widgetSetting=" + widgetSetting +
                    " }";
        else if (prefsAttributes!=null)
            return "Feature: {" +
                    "UI=" + activityName +
                    ",widgetType=" + widgetType +
                    ", preferences="+ prefsAttributes +
                    " }";
        else
            return "Feature: {" +
                    "UI=" + activityName +
                    ", unknow" +
                    " }";
    }


}
