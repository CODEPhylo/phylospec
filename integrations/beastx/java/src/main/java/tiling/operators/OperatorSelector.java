package tiling.operators;

import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;
import tiling.BeastXState;
import tiling.operators.joint.JointOperatorSelector;
import tiling.operators.joint.StrictClockTreeUpDownOperatorSelector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Selects the common operator set used by direct and XML BEAST X output. */
public final class OperatorSelector {

    private static final List<JointOperatorSelector> JOINT_SELECTORS =
            List.of(new StrictClockTreeUpDownOperatorSelector());

    private static final TypeToken<?> SIMPLEX = new TypeToken<Simplex>() {};
    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};
    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};
    private static final TypeToken<?> INT_SCALAR =
            new TypeToken<IntScalar<? extends org.phylospec.domain.Int>>() {};
    private static final TypeToken<?> INT_VECTOR =
            new TypeToken<IntVector<? extends org.phylospec.domain.Int>>() {};

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
        entries.sort(Comparator.comparing(entry -> parameterId(entry.getKey())));

        List<OperatorSpec> operators = new ArrayList<>();
        for (Map.Entry<Parameter, TypeToken<?>> entry : entries) {
            Parameter parameter = entry.getKey();
            TypeToken<?> type = entry.getValue();

            if (isRelaxedClockCategories(state, parameter)) {
                operators.addAll(integerOperators(parameter, 10.0));
            } else if (SIMPLEX.isAssignableFrom(type)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.DELTA_EXCHANGE,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        0.01));
            } else if (isInteger(type)) {
                operators.addAll(integerOperators(
                        parameter,
                        state.operatorConfig.parameterOperatorWeight));
            } else if (hasFiniteBounds(parameter)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        state.operatorConfig.randomWalkWindowSize));
            } else if (isPositive(type)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.SCALE,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        state.operatorConfig.parameterScaleFactor));
            } else {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        state.operatorConfig.randomWalkWindowSize));
            }
        }
        return operators;
    }

    private static List<OperatorSpec> integerOperators(Parameter parameter, double weight) {
        List<OperatorSpec> operators = new ArrayList<>();
        operators.add(parameterOperator(
                OperatorSpec.Family.INTEGER_RANDOM_WALK, parameter, weight, 1.0));

        if (parameter.getDimension() > 1) {
            operators.add(parameterOperator(
                    OperatorSpec.Family.INTEGER_SWAP, parameter, weight, 1.0));
        }
        if (hasFiniteBounds(parameter)) {
            operators.add(parameterOperator(
                    OperatorSpec.Family.INTEGER_UNIFORM, parameter, weight, 1.0));
        }
        return operators;
    }

    private List<OperatorSpec> selectTreeOperators(BeastXState state) {
        List<TreeModel> trees = new ArrayList<>(state.treePriorDistributions.keySet());
        trees.sort(Comparator.comparing(OperatorSelector::treeId));

        List<OperatorSpec> operators = new ArrayList<>();
        BeastXState.OperatorConfig config = state.operatorConfig;
        for (TreeModel tree : trees) {
            if (tree instanceof DefaultTreeModel) {
                operators.add(treeOperator(
                        OperatorSpec.Family.TREE_NODE_HEIGHT_SCALE,
                        tree, config.treeScaleWeight, config.treeScaleFactor));
                operators.add(treeOperator(
                        OperatorSpec.Family.TREE_ROOT_SCALE,
                        tree, config.treeRootScaleWeight, config.treeRootScaleFactor));
            }
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_UNIFORM_HEIGHT,
                    tree, config.treeUniformNodeHeightWeight, 0.0));
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_RANDOM_WALK_HEIGHT,
                    tree, config.treeRandomWalkNodeHeightWeight,
                    config.treeRandomWalkNodeHeightSize));
            if (tree instanceof DefaultTreeModel) {
                operators.add(treeOperator(
                        OperatorSpec.Family.TREE_SUBTREE_SLIDE,
                        tree, config.treeSubtreeSlideWeight, config.treeSubtreeSlideSize));
            }
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_NARROW_EXCHANGE,
                    tree, config.treeNarrowExchangeWeight, 0.0));
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_WIDE_EXCHANGE,
                    tree, config.treeWideExchangeWeight, 0.0));
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_WILSON_BALDING,
                    tree, config.treeWilsonBaldingWeight, 0.0));
        }
        return operators;
    }

    private static OperatorSpec parameterOperator(
            OperatorSpec.Family family,
            Parameter parameter,
            double weight,
            double tuning
    ) {
        return new OperatorSpec(family, parameter, null, weight, tuning);
    }

    private static OperatorSpec treeOperator(
            OperatorSpec.Family family,
            TreeModel tree,
            double weight,
            double tuning
    ) {
        return new OperatorSpec(family, null, tree, weight, tuning);
    }

    private static boolean isPositive(TypeToken<?> type) {
        return type != null && (POSITIVE_REAL_SCALAR.isAssignableFrom(type)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(type));
    }

    private static boolean isInteger(TypeToken<?> type) {
        return type != null && (INT_SCALAR.isAssignableFrom(type)
                || INT_VECTOR.isAssignableFrom(type));
    }

    private static boolean hasFiniteBounds(Parameter parameter) {
        Bounds<Double> bounds = parameter.getBounds();
        if (bounds == null) {
            return false;
        }
        for (int index = 0; index < parameter.getDimension(); index++) {
            if (!Double.isFinite(bounds.getLowerLimit(index))
                    || !Double.isFinite(bounds.getUpperLimit(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRelaxedClockCategories(BeastXState state, Parameter parameter) {
        String id = parameterId(parameter);
        return state.treeRelaxedClockModels.values().stream()
                .map(BeastXState.RelaxedClockSpec::rateCategoriesParameter)
                .anyMatch(candidate -> candidate == parameter
                        || (!id.isBlank() && id.equals(parameterId(candidate))));
    }

    static String parameterId(Parameter parameter) {
        return parameter.getId() == null ? "" : parameter.getId();
    }

    static String treeId(TreeModel tree) {
        return tree.getId() == null ? "" : tree.getId();
    }
}
