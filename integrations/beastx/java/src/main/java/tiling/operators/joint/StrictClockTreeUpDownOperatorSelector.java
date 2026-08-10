package tiling.operators.joint;

import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.operators.OperatorSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Selects joint tree/clock moves for stochastic strict-clock models. */
public final class StrictClockTreeUpDownOperatorSelector implements JointOperatorSelector {

    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};
    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};

    @Override
    public List<OperatorSpec> select(BeastXState state) {
        if (state.operatorConfig.treeClockUpDownWeight <= 0.0) {
            return List.of();
        }

        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(state.treeClockRateParameters.entrySet());
        entries.sort(Comparator.comparing(entry -> id(entry.getKey())));

        List<OperatorSpec> operators = new ArrayList<>();
        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel tree = entry.getKey();
            if (!supportsJointMove(state, tree)) {
                continue;
            }

            entry.getValue().stream()
                    .distinct()
                    .sorted(Comparator.comparing(StrictClockTreeUpDownOperatorSelector::id))
                    .filter(parameter -> isPositive(state.stateNodes.get(parameter)))
                    .map(parameter -> new OperatorSpec(
                            OperatorSpec.Family.TREE_CLOCK_UP_DOWN,
                            parameter,
                            tree,
                            state.operatorConfig.treeClockUpDownWeight,
                            state.operatorConfig.treeClockUpDownScaleFactor
                    ))
                    .forEach(operators::add);
        }
        return List.copyOf(operators);
    }

    private static boolean supportsJointMove(BeastXState state, TreeModel tree) {
        return tree instanceof DefaultTreeModel
                && state.treePriorDistributions.containsKey(tree)
                && !state.treeRelaxedClockModels.containsKey(tree);
    }

    private static boolean isPositive(TypeToken<?> type) {
        return type != null && (POSITIVE_REAL_SCALAR.isAssignableFrom(type)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(type));
    }

    private static String id(Parameter parameter) {
        String id = parameter.getId();
        return id == null ? "" : id;
    }

    private static String id(TreeModel tree) {
        String id = tree.getId();
        return id == null ? "" : id;
    }
}
