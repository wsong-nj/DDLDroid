package tool.staticGuiModel;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.android.axml.AXmlNode;
import tool.basicAnalysis.AppParser;
import tool.basicAnalysis.RecourseMissingException;
import tool.utils.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Widget implements Serializable { // widget may be a Layout
	private static final long serialVersionUID = 1L;

	private String type = null;

	private long androidId = 0;
	private String resName = null;	/* android:id="@+id/resName" */

	private String androidName = null;	// 显示声明要静态添加的Fragment类；
	private String appLayout_behavior = null;
	private boolean appBehavior_hideable = false;

	private String eventMethod = null;					//if equals "openContextMenu", is edge to context menu
	private transient SootMethod eventHandler;	/* Event response method */
	private EventType eventType;				/* click, longclick, slide... */
	private String clickMethodName;

	private boolean whetherCheck = false;

	private List<Widget> children = new ArrayList<>();
	private Widget parent = null;
	private int h;


	public Widget() {}
	public Widget(AXmlNode node){
		this.setType(node.getTag());
		if(node.hasAttribute("id")) {
			Object id = node.getAttribute("id").getValue();
			if(id instanceof Integer) {
				int resId = (int) id;
				this.setAndroidId(resId);
				try {
					String resName = AppParser.v().getWidgetNameById(resId);
					this.setResName(resName);
				}catch (Exception e) {
					Logger.e("[Widget]", e);
				}
				this.setWhetherCheck(true); // 有ID证明是会被调用赋值的。
			}

		}
		if (node.hasAttribute("name")){
			Object o = node.getAttribute("name").getValue();
			if (o instanceof String){
				this.setAndroidName((String) o);
			}

		}
		if (node.hasAttribute("layout_behavior")){
			Object lb = node.getAttribute("layout_behavior").getValue();
			if(lb instanceof Integer){
				int StringId = (int) lb;
					String lbValue = AppParser.v().getStringValueById(StringId);
					this.setAppLayout_behavior(lbValue);
			}else if(lb instanceof String){
				this.setAppLayout_behavior((String) lb);
			}
		}
		if (node.hasAttribute("behavior_hideable")){
			Object o = node.getAttribute("behavior_hideable").getValue();
			if (o instanceof Boolean){
				this.setAppBehavior_hideable((Boolean) o);
			}

		}
		if (node.hasAttribute("onClick")){
			Object o = node.getAttribute("onClick").getValue();
			if (o instanceof String){
				this.setClickMethodName((String) o);
			}
		}
//		setEventMethod(String eventMethod)
//		setEventHandler(SootMethod eventHandler)
//		setEventType(EventType eventType)

	}

	//================Setter=====================
	public void setType(String type) {
		this.type = type;
	}
	public void setAndroidId(long androidId) {
		this.androidId = androidId;
	}
	public void setResName(String resName) {
		this.resName = resName;
	}
	public void setAndroidName(String androidName) {
		this.androidName = androidName;
	}
	public void setAppLayout_behavior(String appLayout_behavior) {
		this.appLayout_behavior = appLayout_behavior;
	}
	public void setAppBehavior_hideable(boolean appBehavior_hideable) {
		this.appBehavior_hideable = appBehavior_hideable;
	}
	public void setEventMethod(String eventMethod) {
		this.eventMethod = eventMethod;
	}
	public void setEventHandler(SootMethod eventHandler) {
		this.eventHandler = eventHandler;
	}
	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}
	public void setChildren(List<Widget> children) {
		this.children = children;
	}
	public void setParent(Widget parent) {
		this.parent = parent;
	}
	public void setWhetherCheck(boolean whetherCheck) {
		this.whetherCheck = whetherCheck;
	}
	public void setH(int h) {
		this.h = h;
	}

	public void setClickMethodName(String clickMethodName) {
		this.clickMethodName = clickMethodName;
	}

	//================Getter=====================
	public String getType() {
		return type;
	}
	public long getAndroidId() {
		return androidId;
	}
	public String getResName() {
		return resName;
	}
	public String getAndroidName() {
		return androidName;
	}
	public String getAppLayout_behavior(){
		return appLayout_behavior;
	}
	public boolean getAppBehavior_hideable(){
		return appBehavior_hideable;
	}
	public String getEventMethod() {
		return eventMethod;
	}
	public SootMethod getEventHandler() {
		return eventHandler;
	}
	public EventType getEventType() {
		return eventType;
	}
	public List<Widget> getChildren() {
		return children;
	}
	public Widget getParent() {
		return parent;
	}
	public boolean isWhetherCheck() {
		return whetherCheck;
	}
	public int getH() {
		return h;
	}

	public String getClickMethodName() {
		return clickMethodName;
	}

	//=================Add=======================
	
	public void addChildWidget(Widget widget) {
		children.add(widget);
	}
	
	//=================Other=====================
	public void displayWidget(){
		System.out.println("Type=  "+type);
		System.out.println("Android:Id=  "+androidId);
		System.out.println("ResName=  "+resName);
		System.out.println("Android:Name=  "+androidName);
		System.out.println("App:Layout_behavior=  "+appLayout_behavior);
		System.out.println("App:Behavior_hideable=  "+appBehavior_hideable);
	}

//	@Override
//	public String toString() {
//		StringBuilder sb = new StringBuilder();
//		sb.append("<(").append(id).append(")").append(type).append(", ");
//		if(resName != null)
//			sb.append(resName);
//		sb.append(", ").append(resId).append(", ");
//		if(text != null)
//			sb.append(text);
//		sb.append(", ").append(eventType).append(">;");
//		return sb.toString();
//	}
//
//	public String toCsv() {
//		StringBuilder sb = new StringBuilder();
//		sb.append("<").append(id).append("|").append(type).append("|");
//		if(resName != null)
//			sb.append(resName);
//		sb.append("|").append(resId).append("|");
//		if(text != null)
//			sb.append(text);
//		sb.append("|").append(eventType).append(">;");
//		return sb.toString();
//	}
}
