package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.params.BeastXIntVectorParam;
import tiling.params.BeastXRealScalarParam;
import tiling.params.BeastXRealVectorParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.Arrays;
import java.util.IdentityHashMap;

public final class IIDTile {

    private IIDTile() {
    }

    public static final class RealIID extends GeneratorTile<
            BoundDistribution<
                    BeastXRealVectorParam<org.phylospec.domain.Real>,
                    DistributionLikelihood
                    >,
            BeastXState
            > {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "IID";
        }

        GeneratorTileInput<
                BoundDistribution<
                        BeastXRealScalarParam<org.phylospec.domain.Real>,
                        DistributionLikelihood
                        >,
                BeastXState
                > baseInput =
                new GeneratorTileInput<>("base");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numInput =
                new GeneratorTileInput<>("num");

        @Override
        public BoundDistribution<
                BeastXRealVectorParam<org.phylospec.domain.Real>,
                DistributionLikelihood
                > applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            BoundDistribution<
                    BeastXRealScalarParam<org.phylospec.domain.Real>,
                    DistributionLikelihood
                    > base =
                    this.baseInput.apply(beastState, indexVariables);

            int dimension =
                    getDimension(this.numInput.apply(beastState, indexVariables));

            double[] defaultValues =
                    repeatedDefaultValues(
                            base.stateNode.getParameter().getParameterValue(0),
                            dimension
                    );

            BeastXRealVectorParam<org.phylospec.domain.Real> defaultState =
                    new BeastXRealVectorParam<>(
                            boundedVector(defaultValues, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY),
                            org.phylospec.domain.Real.INSTANCE
                    );

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(state.getParameter())
            );
        }
    }

    public static final class PositiveRealIID extends GeneratorTile<
            BoundDistribution<
                    BeastXRealVectorParam<org.phylospec.domain.PositiveReal>,
                    DistributionLikelihood
                    >,
            BeastXState
            > {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "IID";
        }

        GeneratorTileInput<
                BoundDistribution<
                        BeastXRealScalarParam<org.phylospec.domain.PositiveReal>,
                        DistributionLikelihood
                        >,
                BeastXState
                > baseInput =
                new GeneratorTileInput<>("base");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numInput =
                new GeneratorTileInput<>("num");

        @Override
        public BoundDistribution<
                BeastXRealVectorParam<org.phylospec.domain.PositiveReal>,
                DistributionLikelihood
                > applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            BoundDistribution<
                    BeastXRealScalarParam<org.phylospec.domain.PositiveReal>,
                    DistributionLikelihood
                    > base =
                    this.baseInput.apply(beastState, indexVariables);

            int dimension =
                    getDimension(this.numInput.apply(beastState, indexVariables));

            double[] defaultValues =
                    repeatedDefaultValues(
                            base.stateNode.getParameter().getParameterValue(0),
                            dimension
                    );

            BeastXRealVectorParam<org.phylospec.domain.PositiveReal> defaultState =
                    new BeastXRealVectorParam<>(
                            boundedVector(defaultValues, 0.0, Double.POSITIVE_INFINITY),
                            org.phylospec.domain.PositiveReal.INSTANCE
                    );

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(state.getParameter())
            );
        }
    }

    /** IID draws from a distribution on (0, 1), such as Beta. */
    public static final class UnitIntervalIID extends GeneratorTile<
            BoundDistribution<BeastXRealVectorParam<UnitInterval>, DistributionLikelihood>,
            BeastXState
            > {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "IID";
        }

        GeneratorTileInput<
                BoundDistribution<BeastXRealScalarParam<UnitInterval>, DistributionLikelihood>,
                BeastXState
                > baseInput =
                new GeneratorTileInput<>("base");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numInput =
                new GeneratorTileInput<>("num");

        @Override
        public BoundDistribution<BeastXRealVectorParam<UnitInterval>, DistributionLikelihood> applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            BoundDistribution<BeastXRealScalarParam<UnitInterval>, DistributionLikelihood> base =
                    this.baseInput.apply(beastState, indexVariables);

            double[] defaultValues =
                    repeatedDefaultValues(
                            base.stateNode.getParameter().getParameterValue(0),
                            getDimension(this.numInput.apply(beastState, indexVariables))
                    );

            BeastXRealVectorParam<UnitInterval> defaultState =
                    new BeastXRealVectorParam<>(boundedVector(defaultValues, 0.0, 1.0), UnitInterval.INSTANCE);

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(state.getParameter())
            );
        }
    }

    /** IID draws from a distribution on the non-negative integers, such as Poisson. */
    public static final class NonNegativeIntIID extends GeneratorTile<
            BoundDistribution<BeastXIntVectorParam<NonNegativeInt>, DistributionLikelihood>,
            BeastXState
            > {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "IID";
        }

        GeneratorTileInput<
                BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood>,
                BeastXState
                > baseInput =
                new GeneratorTileInput<>("base");

        GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numInput =
                new GeneratorTileInput<>("num");

        @Override
        public BoundDistribution<BeastXIntVectorParam<NonNegativeInt>, DistributionLikelihood> applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood> base =
                    this.baseInput.apply(beastState, indexVariables);

            double[] defaultValues =
                    repeatedDefaultValues(
                            base.stateNode.getParameter().getParameterValue(0),
                            getDimension(this.numInput.apply(beastState, indexVariables))
                    );

            BeastXIntVectorParam<NonNegativeInt> defaultState =
                    new BeastXIntVectorParam<>(
                            boundedVector(defaultValues, 0.0, Double.POSITIVE_INFINITY),
                            NonNegativeInt.INSTANCE
                    );

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(state.getParameter())
            );
        }
    }

    /**
     * A vector parameter with the same bounds for every element. BEAST X throws when the bounds of
     * a parameter without bounds are read, so unbounded vectors get infinite bounds.
     */
    private static Parameter boundedVector(double[] values, double lower, double upper) {
        Parameter parameter = new Parameter.Default(values);
        parameter.addBounds(new Parameter.DefaultBounds(upper, lower, values.length));
        return parameter;
    }

    private static int getDimension(IntScalar<? extends PositiveInt> num) {
        int dimension =
                num.get();

        if (dimension <= 0) {
            throw new TileApplicationError(
                    "IID num must be positive.",
                    "Use num >= 1."
            );
        }

        return dimension;
    }

    private static double[] repeatedDefaultValues(double defaultValue, int dimension) {
        double[] defaultValues =
                new double[dimension];

        Arrays.fill(
                defaultValues,
                defaultValue
        );

        return defaultValues;
    }
}