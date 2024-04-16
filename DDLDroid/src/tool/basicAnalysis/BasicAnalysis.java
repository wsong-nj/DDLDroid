package tool.basicAnalysis;

import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.util.Chain;

import java.util.List;

public class BasicAnalysis {

    public Value getInvokeExprCaller(InvokeExpr invokeExpr, Chain<Local> bodyLocals){
        List<ValueBox> useboxes = invokeExpr.getUseBoxes();
        if (useboxes.size() == 1){
            return useboxes.get(0).getValue();
        }else if (useboxes.size() == 0){
            return null;
        }else {
            for (ValueBox valueBox : useboxes){
            }
            return null;
        }
    }
}


