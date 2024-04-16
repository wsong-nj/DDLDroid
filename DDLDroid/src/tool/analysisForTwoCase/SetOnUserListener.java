package tool.analysisForTwoCase;

import java.util.ArrayList;
import java.util.List;

public class SetOnUserListener {
    private static final List<String> listener = new ArrayList<>();
    static {
        listener.add("setOnClickListener");
        listener.add("setOnDragListener");
        listener.add("setOnHoverListener");
        listener.add("setOnEditorActionListener");
        listener.add("setOnKeyListener");
        listener.add("setOnTouchListener");
        listener.add("setOnCapturedPointerListener");
        listener.add("setOnContextClickListener");
        listener.add("setOnFocusChangeListener");
        listener.add("setOnGenericMotionListener");
        listener.add("setOnLongClickListener");
        listener.add("setOnScrollChangeListener");
        listener.add("setOnApplyWindowInsetsListener");
        listener.add("setOnCreateContextMenuListener");
        listener.add("setOnSystemUiVisibilityChangeListener");
    }

    // 不同接口对应相应的需要实现的方法名字。 参数可能不同，但是目前只考虑文件名
    private static final List<String> ListenerAPI = new ArrayList<>();
    static {
        ListenerAPI.add("OnClick");
        ListenerAPI.add("OnDrag");
        ListenerAPI.add("OnHover");
        ListenerAPI.add("OnEditorAction");
        ListenerAPI.add("OnKey");
        ListenerAPI.add("OnTouch");
        ListenerAPI.add("OnCapturedPointer");
        ListenerAPI.add("OnContextClick");
        ListenerAPI.add("OnFocusChange");
        ListenerAPI.add("OnGenericMotion");
        ListenerAPI.add("OnLongClick");
        ListenerAPI.add("OnScrollChange");
        ListenerAPI.add("OnApplyWindowInsets");
        ListenerAPI.add("OnCreateContextMenu");
        ListenerAPI.add("OnSystemUiVisibilityChange");
        // 以下三个为activity特有的可以实现的接口，Button和TextView等控件没有。
        /*
        ListenerAPI.add("OnLayoutChange");
        ListenerAPI.add("OnAttachStateChange");
        ListenerAPI.add("OnUnhandledKeyEvent");
        */
    }

    // android.view.View$OnClickListener
    // android.content.DialogInterface$OnClickListener

    public static List<String> getListener(){
        return listener;
    }

}
