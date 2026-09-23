package tiling.operators;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.tiling.TypeToken;
import tiling.BeastXState;
import tiling.operators.joint.JointOperatorSelector;
import tiling.operators.joint.TreeClockUpDownOperatorSelector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Collects the BEAST X operator schedule used by direct and XML output.
 *
 * <p>Model roles currently take precedence, while unclaimed PhyloSpec
 * parameters receive type-based fallback operators from the BEAUti policy.</p>
 */
public final class OperatorSelector {

    private static final Beauti105OperatorPolicy POLICY =
            new Beauti105OperatorPolicy();

    private static final List<JointOperatorSelector> JOINT_SELECTORS =
            List.of(new TreeClockUpDownOperatorSelector());

    public List<OperatorSpec> select(BeastXState state) {
        List<OperatorSpec> operators = new ArrayList<>();

        operators.addAll(selectParameterOperators(state));
        operators.addAll(selectTreeOperators(state));

        for (JointOperatorSelector selector : JOINT_SELECTORS) {
            operators.addAll(selector.select(state));
        }

        return List.copyOf(operators);
    }

    private List<OperatorSpec> selectParameterOperators(BeastXState state) {
        List<Map.Entry<Parameter, TypeToken<?>>> entries =
                new ArrayList<>(state.stateNodes.entrySet());

        entries.sort(
                Comparator.comparing(
                        entry -> parameterId(entry.getKey())
                )
        );

        List<OperatorSpec> operators = new ArrayList<>();

        for (Map.Entry<Parameter, TypeToken<?>> entry : entries) {
            Parameter parameter = entry.getKey();

            operators.addAll(POLICY.parameterOperators(
                    parameter,
                    entry.getValue(),
                    state.getParameterRoles(parameter),
                    state.operatorConfig
            ));
        }

        return operators;
    }

    private List<OperatorSpec> selectTreeOperators(BeastXState state) {
        List<TreeModel> trees =
                new ArrayList<>(state.treePriorDistributions.keySet());

        trees.sort(Comparator.comparing(OperatorSelector::treeId));

        List<OperatorSpec> operators = new ArrayList<>();

        for (TreeModel tree : trees) {
            operators.addAll(POLICY.treeOperators(tree));
        }

        return operators;
    }

    static String parameterId(Parameter parameter) {
        return parameter.getId() == null
                ? ""
                : parameter.getId();
    }

    static String treeId(TreeModel tree) {
        return tree.getId() == null
                ? ""
                : tree.getId();
    }
}