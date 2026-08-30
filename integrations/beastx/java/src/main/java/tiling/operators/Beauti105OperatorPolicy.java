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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provides the supported subset of the BEAUti 10.5 default operator policy.
 *
 * <p>This class determines operator families, weights, tuning values, and
 * tree-weight formulas. It does not decide which model component owns
 * operator registration.</p>
 */
final class Beauti105OperatorPolicy {

    private static final double NO_TUNING = 0.0;
    private static final double DELTA_EXCHANGE_DELTA = 0.01;
    private static final double SUBTREE_LEAP_SIZE = 1.0;

    private static final TypeToken<?> SIMPLEX =
            new TypeToken<Simplex>() {};

    private static final TypeToken<?> POSITIVE_REAL_SCALAR =
            new TypeToken<RealScalar<? extends PositiveReal>>() {};

    private static final TypeToken<?> POSITIVE_REAL_VECTOR =
            new TypeToken<RealVector<? extends PositiveReal>>() {};

    private static final TypeToken<?> INT_SCALAR =
            new TypeToken<IntScalar<? extends org.phylospec.domain.Int>>() {};

    private static final TypeToken<?> INT_VECTOR =
            new TypeToken<IntVector<? extends org.phylospec.domain.Int>>() {};

    private static final TypeToken<?> BOOLEAN =
            new TypeToken<BoolScalar>() {};

    List<OperatorSpec> parameterOperators(
            Parameter parameter,
            TypeToken<?> type,
            Set<ParameterRole> roles,
            BeastXState.OperatorConfig config
    ) {
        if (roles.contains(ParameterRole.RELAXED_CLOCK_CATEGORIES)) {
            return List.of(
                    parameterOperator(
                            OperatorSpec.Family.INTEGER_SWAP,
                            parameter,
                            config.relaxedClockCategoryWeight,
                            1.0
                    ),
                    parameterOperator(
                            OperatorSpec.Family.INTEGER_UNIFORM,
                            parameter,
                            config.relaxedClockCategoryWeight,
                            1.0
                    )
            );
        }

        if (roles.contains(ParameterRole.SUBSTITUTION_SIMPLEX)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.DELTA_EXCHANGE,
                    parameter,
                    config.substitutionOperatorWeight,
                    DELTA_EXCHANGE_DELTA
            ));
        }

        if (roles.contains(ParameterRole.SITE_MODEL_PROPORTION)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.RANDOM_WALK_LOGIT,
                    parameter,
                    config.siteModelOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.TREE_PRIOR_PROPORTION)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.RANDOM_WALK_LOGIT,
                    parameter,
                    config.demographicOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.SERIAL_TREE_PRIOR_PROPORTION)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.RANDOM_WALK_LOGIT,
                    parameter,
                    config.serialTreePriorOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.DEMOGRAPHIC_GROWTH_RATE)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.RANDOM_WALK,
                    parameter,
                    config.demographicOperatorWeight,
                    config.randomWalkWindowSize
            ));
        }

        if (roles.contains(ParameterRole.CLOCK_RATE)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.SCALE,
                    parameter,
                    config.clockRateOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.DEMOGRAPHIC_SCALE)
                || roles.contains(ParameterRole.TREE_PRIOR_SCALE)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.SCALE,
                    parameter,
                    config.demographicOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.SERIAL_TREE_PRIOR_SCALE)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.SCALE,
                    parameter,
                    config.serialTreePriorOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        if (roles.contains(ParameterRole.SUBSTITUTION_SCALE)
                || roles.contains(ParameterRole.SITE_MODEL_SCALE)) {
            double weight =
                    roles.contains(ParameterRole.SITE_MODEL_SCALE)
                            ? config.siteModelOperatorWeight
                            : config.substitutionOperatorWeight;

            return List.of(parameterOperator(
                    OperatorSpec.Family.SCALE,
                    parameter,
                    weight,
                    config.parameterScaleFactor
            ));
        }

        return fallbackParameterOperators(parameter, type, config);
    }

    List<OperatorSpec> treeOperators(TreeModel tree) {
        double subtreeLeapWeight =
                Math.max(tree.getExternalNodeCount(), 30.0);

        double fixedHeightSprWeight =
                Math.max(subtreeLeapWeight / 10.0, 3.0);

        return List.of(
                treeOperator(
                        OperatorSpec.Family.TREE_SUBTREE_LEAP,
                        tree,
                        subtreeLeapWeight,
                        SUBTREE_LEAP_SIZE
                ),
                treeOperator(
                        OperatorSpec.Family.TREE_FIXED_HEIGHT_SPR,
                        tree,
                        fixedHeightSprWeight,
                        NO_TUNING
                )
        );
    }

    private List<OperatorSpec> fallbackParameterOperators(
            Parameter parameter,
            TypeToken<?> type,
            BeastXState.OperatorConfig config
    ) {
        if (BOOLEAN.isAssignableFrom(type)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.BIT_FLIP,
                    parameter,
                    config.parameterOperatorWeight,
                    NO_TUNING
            ));
        }

        if (SIMPLEX.isAssignableFrom(type)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.DELTA_EXCHANGE,
                    parameter,
                    config.parameterOperatorWeight,
                    DELTA_EXCHANGE_DELTA
            ));
        }

        if (isInteger(type)) {
            return integerOperators(
                    parameter,
                    config.parameterOperatorWeight
            );
        }

        if (hasFiniteBounds(parameter)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.RANDOM_WALK,
                    parameter,
                    config.parameterOperatorWeight,
                    config.randomWalkWindowSize
            ));
        }

        if (isPositive(type)) {
            return List.of(parameterOperator(
                    OperatorSpec.Family.SCALE,
                    parameter,
                    config.parameterOperatorWeight,
                    config.parameterScaleFactor
            ));
        }

        return List.of(parameterOperator(
                OperatorSpec.Family.RANDOM_WALK,
                parameter,
                config.parameterOperatorWeight,
                config.randomWalkWindowSize
        ));
    }

    private List<OperatorSpec> integerOperators(
            Parameter parameter,
            double weight
    ) {
        List<OperatorSpec> operators = new ArrayList<>();

        operators.add(parameterOperator(
                OperatorSpec.Family.INTEGER_RANDOM_WALK,
                parameter,
                weight,
                1.0
        ));

        if (parameter.getDimension() > 1) {
            operators.add(parameterOperator(
                    OperatorSpec.Family.INTEGER_SWAP,
                    parameter,
                    weight,
                    1.0
            ));
        }

        if (hasFiniteBounds(parameter)) {
            operators.add(parameterOperator(
                    OperatorSpec.Family.INTEGER_UNIFORM,
                    parameter,
                    weight,
                    1.0
            ));
        }

        return List.copyOf(operators);
    }

    private static OperatorSpec parameterOperator(
            OperatorSpec.Family family,
            Parameter parameter,
            double weight,
            double tuning
    ) {
        return new OperatorSpec(
                family,
                parameter,
                null,
                weight,
                tuning
        );
    }

    private static OperatorSpec treeOperator(
            OperatorSpec.Family family,
            TreeModel tree,
            double weight,
            double tuning
    ) {
        return new OperatorSpec(
                family,
                null,
                tree,
                weight,
                tuning
        );
    }

    private static boolean isPositive(TypeToken<?> type) {
        return type != null
                && (POSITIVE_REAL_SCALAR.isAssignableFrom(type)
                || POSITIVE_REAL_VECTOR.isAssignableFrom(type));
    }

    private static boolean isInteger(TypeToken<?> type) {
        return type != null
                && (INT_SCALAR.isAssignableFrom(type)
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
}
