package tool.analysisForTwoCase;

import heros.solver.PathEdge;
import soot.Unit;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

import java.util.Set;

public class RecordBackwardsPropagationHandler implements TaintPropagationHandler {

    private static int proagated=0;
    private static int newone=0;

    /**
     * Handler function that is invoked when a taint is proagated in the data flow
     * engine
     *
     * @param stmt    The statement over which the taint is propagated
     * @param taint   The taint being propagated
     * @param manager The manager object that gives access to the data flow engine
     * @param type
     */
    @Override
    public void notifyFlowIn(Unit stmt, Abstraction taint, InfoflowManager manager, FlowFunctionType type) {

//
//        proagated++;
//
//        IInfoflowCFG icfg = manager.getICFG();
//
//        System.out.println("\n\n新反向传播"+proagated+"***********************************************************************");
//
//        System.out.println("[STMT :] "+stmt + "\n\tFROM  "+icfg.getMethodOf(stmt));
//        System.out.println("[正在传播的污点:] "+taint);
//        System.out.println("[正在处理的FlowFunctionType:] "+type);
//
//        System.out.println("----------------------------------------------------------------------");
    }

    /**
     * Handler function that is invoked when a new taint is generated in the data
     * flow engine
     *
     * @param stmt     The statement over which the taint is propagated
     * @param d1       The abstraction at the beginning of the current method
     * @param incoming The original abstraction from which the outgoing ones were
     *                 computed
     * @param outgoing The set of taints being propagated
     * @param manager  The manager object that gives access to the data flow engine
     * @param type     The type of data flow edge being processed
     * @return The new abstractions to be propagated on. If you do not want to
     * change the normal propagation behavior, just return the value of the
     * "taints" parameter as-is.
     */
    @Override
    public Set<Abstraction> notifyFlowOut(Unit stmt, Abstraction d1, Abstraction incoming, Set<Abstraction> outgoing, InfoflowManager manager, FlowFunctionType type) {
        // Inject the new alias into the forward solver
//        for (Unit u : interproceduralCFG().getPredsOf(defStmt))
//            manager.getForwardSolver()
//                    .processEdge(new PathEdge<Unit, Abstraction>(d1, u, newAbs));
//        manager.getForwardSolver().processEdge(new PathEdge<Unit, Abstraction>(d1, u, newAbs));
//        newone++;
//        IInfoflowCFG icfg = manager.getICFG();
//
//        System.out.println("\n\n新别名"+newone+"***********************************************************************");
//
//        System.out.println("\tUnit是: "+stmt+"\n\tFROM  "+icfg.getMethodOf(stmt));
//        System.out.println("\td1是: "+d1.getSourceContext());
//        System.out.println("\tincoming是: "+incoming.getSourceContext());
//        System.out.println("\toutgoing是: ");
//        System.out.println(outgoing);
////        for (Abstraction abs : outgoing) {
////            //System.out.println(abs.getSourceContext());
////            //System.out.println("\t\t" + abs.getCurrentStmt() + " -=- " + icfg.getMethodOf(abs.getCurrentStmt()));
////        }
//        System.out.println("\tFlowFunctionType是: "+type);
//        System.out.println("***********************************************************************");

        return outgoing;
    }
}
