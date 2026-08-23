package tiling.operators;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.BoolScalar;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import org.phylospec.types.Simplex;
import tiling.BeastXState;
import tiling.operators.joint.JointOperatorSelector;
import tiling.operators.joint.TreeClockUpDownOperatorSelector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects the supported subset of the BEAUti 10.5 default operator set used by
 * direct and XML output.
 * Model roles take precedence; unclaimed PhyloSpec parameters use type-based
 * fallback operators so backend-specific models remain usable.
 */
public final class OperatorSelector {

    private static final double NO_TUNING = 0.0;
    private static final double BEAUTI_DELTA = 0.01;
    private static final double BEAUTI_SUBTREE_LEAP_SIZE = 1.0;

    private static final List<JointOperatorSelector> JOINT_SELECTORS =
            List.of(new TreeClockUpDownOperatorSelector());

    private static final TypeToken<?> SIMPLEX = new TypeToken<Simplex>() {};
    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};
    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};
    private static final TypeToken<?> INT_SCALAR =
            new TypeToken<IntScalar<? extends org.phylospec.domain.Int>>() {};
    private static final TypeToken<?> INT_VECTOR =
            new TypeToken<IntVector<? extends org.phylospec.domain.Int>>() {};
    private static final TypeToken<?> BOOLEAN = new TypeToken<BoolScalar>() {};

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
            Set<ParameterRole> roles = state.getParameterRoles(parameter);
            BeastXState.OperatorConfig config = state.operatorConfig;

            if (roles.contains(ParameterRole.RELAXED_CLOCK_CATEGORIES)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.INTEGER_SWAP,
                        parameter,
                        config.relaxedClockCategoryWeight,
                        1.0));
                operators.add(parameterOperator(
                        OperatorSpec.Family.INTEGER_UNIFORM,
                        parameter,
                        config.relaxedClockCategoryWeight,
                        1.0));
            } else if (roles.contains(ParameterRole.SUBSTITUTION_SIMPLEX)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.DELTA_EXCHANGE,
                        parameter,
                        config.substitutionOperatorWeight,
                        BEAUTI_DELTA));
            } else if (roles.contains(ParameterRole.SITE_MODEL_PROPORTION)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK_LOGIT,
                        parameter,
                        config.siteModelOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.TREE_PRIOR_PROPORTION)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK_LOGIT,
                        parameter,
                        config.demographicOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.SERIAL_TREE_PRIOR_PROPORTION)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK_LOGIT,
                        parameter,
                        config.serialTreePriorOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.DEMOGRAPHIC_GROWTH_RATE)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.RANDOM_WALK,
                        parameter,
                        config.demographicOperatorWeight,
                        config.randomWalkWindowSize));
            } else if (roles.contains(ParameterRole.CLOCK_RATE)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.SCALE,
                        parameter,
                        config.clockRateOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.DEMOGRAPHIC_SCALE)
                    || roles.contains(ParameterRole.TREE_PRIOR_SCALE)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.SCALE,
                        parameter,
                        config.demographicOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.SERIAL_TREE_PRIOR_SCALE)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.SCALE,
                        parameter,
                        config.serialTreePriorOperatorWeight,
                        config.parameterScaleFactor));
            } else if (roles.contains(ParameterRole.SUBSTITUTION_SCALE)
                    || roles.contains(ParameterRole.SITE_MODEL_SCALE)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.SCALE,
                        parameter,
                        roles.contains(ParameterRole.SITE_MODEL_SCALE)
                                ? config.siteModelOperatorWeight
                                : config.substitutionOperatorWeight,
                        config.parameterScaleFactor));
            } else if (BOOLEAN.isAssignableFrom(type)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.BIT_FLIP,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        NO_TUNING));
            } else if (SIMPLEX.isAssignableFrom(type)) {
                operators.add(parameterOperator(
                        OperatorSpec.Family.DELTA_EXCHANGE,
                        parameter,
                        state.operatorConfig.parameterOperatorWeight,
                        BEAUTI_DELTA));
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
        for (TreeModel tree : trees) {
            double subtreeLeapWeight = Math.max(tree.getExternalNodeCount(), 30.0);
            double fixedHeightSprWeight = Math.max(subtreeLeapWeight / 10.0, 3.0);

            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_SUBTREE_LEAP,
                    tree,
                    subtreeLeapWeight,
                    BEAUTI_SUBTREE_LEAP_SIZE));
            operators.add(treeOperator(
                    OperatorSpec.Family.TREE_FIXED_HEIGHT_SPR,
                    tree,
                    fixedHeightSprWeight,
                    NO_TUNING));
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

    static String parameterId(Parameter parameter) {
        return parameter.getId() == null ? "" : parameter.getId();
    }

    static String treeId(TreeModel tree) {
        return tree.getId() == null ? "" : tree.getId();
    }
}
