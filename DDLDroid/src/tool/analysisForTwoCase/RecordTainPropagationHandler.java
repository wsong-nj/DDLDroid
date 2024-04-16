package tool.analysisForTwoCase;

import soot.Unit;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 前向传播处理器 */
public class RecordTainPropagationHandler implements TaintPropagationHandler {

    private static int proagated=0;
    private static int newone=0;

    private List<StringBuffer> listForward;


    public RecordTainPropagationHandler() {
        //System.out.println("污染传播构造函数");
        listForward = new ArrayList<>();
    }



    /**
     * Handler function that is invoked when a taint is proagated in the data flow
     * engine
     *
     * @param stmt
     *            The statement over which the taint is propagated
     * @param taint
     *            The taint being propagated
     * @param manager
     *            The manager object that gives access to the data flow engine
     * @param type
     *            The type of data flow edge being processed
     */
    @Override
    public void notifyFlowIn(Unit stmt, Abstraction taint, InfoflowManager manager, FlowFunctionType type) {

        //manager.getAliasing().

//        proagated++;
//
//        IInfoflowCFG icfg = manager.getICFG();
//
//        StringBuffer sb = new StringBuffer();
//        sb.append("\n\n新传播"+proagated+"***********************************************************************");
//        sb.append("\n[STMT :] "+stmt + "\n\tFROM  "+icfg.getMethodOf(stmt));
//        sb.append("\n[正在传播的污点:] "+taint.getSourceContext());
//        sb.append("\n[正在处理的FlowFunctionType:] "+type);
//        sb.append("\n----------------------------------------------------------------------");
//        listForward.add(sb);

//        System.out.println("\n\n新传播"+proagated+"***********************************************************************");
//        System.out.println("[STMT :] "+stmt + "\n\tFROM  "+icfg.getMethodOf(stmt));
//        System.out.println("[正在传播的污点:] "+taint.getSourceContext());
//        System.out.println("[正在处理的FlowFunctionType:] "+type);
//
//        if (((Stmt)stmt).containsInvokeExpr()){
//            if ( ((Stmt) stmt).getInvokeExpr().getMethod().getName().equals("setString") ){
//                ;
//            }
//        }
//
//        System.out.println("----------------------------------------------------------------------");

    }

    /**
     * Handler function that is invoked when a new taint is generated in the data
     * flow engine
     *
     * @param stmt
     *            The statement over which the taint is propagated
     * @param d1
     *            The abstraction at the beginning of the current method
     * @param incoming
     *            The original abstraction from which the outgoing ones were
     *            computed
     * @param outgoing
     *            The set of taints being propagated
     * @param manager
     *            The manager object that gives access to the data flow engine
     * @param type
     *            The type of data flow edge being processed
     * @return The new abstractions to be propagated on. If you do not want to
     *         change the normal propagation behavior, just return the value of the
     *         "taints" parameter as-is.
     */
    @Override
    public Set<Abstraction> notifyFlowOut(Unit stmt, Abstraction d1, Abstraction incoming, Set<Abstraction> outgoing,
                                          InfoflowManager manager, FlowFunctionType type) {

//        newone++;
//
//        IInfoflowCFG icfg = manager.getICFG();
//
//        System.out.println("\n\n新污染"+newone+"***********************************************************************");
//
//        System.out.println("\tUnit是: "+stmt+"\n\tFROM  "+icfg.getMethodOf(stmt));
//        System.out.println("\td1是: "+d1.getSourceContext());
//        System.out.println("\tincoming是: "+incoming.getSourceContext());
//        System.out.println("\toutgoing是: ");
//        System.out.println(outgoing);
////        for (Abstraction abs : outgoing) {
////            System.out.println("\t\t" + abs.getSourceContext());
////            //System.out.println("\t\t" + abs.getCurrentStmt() + " -=- " + icfg.getMethodOf(abs.getCurrentStmt()));
////        }
//        System.out.println("\tFlowFunctionType是: "+type);
//        System.out.println("***********************************************************************");
        return outgoing;
    }

    public void showForward(){
        for (StringBuffer sb : listForward){
            System.out.println(sb.toString());
        }
    }

}
