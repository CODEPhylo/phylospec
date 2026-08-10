package tiling.operators.joint;

import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.RealScalar;
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

    @Override
    public List<OperatorSpec> select(BeastXState state) {
        if (state.operatorConfig.treeClockUpDownWeight <= 0.0) {
            return List.of();
        }

        List<Map.Entry<TreeModel, List<Parameter>>> entries =
                new ArrayList<>(state.treeStrictClockRateParameters.entrySet());
        entries.sort(Comparator.comparing(entry -> treeId(entry.getKey())));

        List<OperatorSpec> operators = new ArrayList<>();
        for (Map.Entry<TreeModel, List<Parameter>> entry : entries) {
            TreeModel tree = entry.getKey();
            if (!(tree instanceof DefaultTreeModel)
                    || !state.treePriorDistributions.containsKey(tree)) {
                continue;
            }

            entry.getValue().stream()
                    .distinct()
                    .sorted(Comparator.comparing(StrictClockTreeUpDownOperatorSelector::parameterId))
                    .filter(parameter -> isPositiveClockRate(state, parameter))
                    .map(parameter -> new OperatorSpec(
                            OperatorSpec.Family.TREE_CLOCK_UP_DOWN,
                            parameter,
                            tree,
                            state.operatorConfig.treeClockUpDownWeight,
                            state.operatorConfig.treeClockUpDownScaleFactor))
                    .forEach(operators::add);
        }
        return List.copyOf(operators);
    }

    private static boolean isPositiveClockRate(BeastXState state, Parameter parameter) {
        TypeToken<?> type = state.stateNodes.get(parameter);
        if (type == null) {
            String id = parameterId(parameter);
            type = state.stateNodes.entrySet().stream()
                    .filter(entry -> !id.isBlank() && id.equals(parameterId(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return type != null && POSITIVE_REAL_SCALAR.isAssignableFrom(type);
    }

    private static String parameterId(Parameter parameter) {
        return parameter.getId() == null ? "" : parameter.getId();
    }

    private static String treeId(TreeModel tree) {
        return tree.getId() == null ? "" : tree.getId();
    }
}
