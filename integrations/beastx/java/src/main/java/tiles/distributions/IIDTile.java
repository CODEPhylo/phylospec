package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
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
                            new Parameter.Default(defaultValues),
                            org.phylospec.domain.Real.INSTANCE
                    );

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(new Attribute.Default<>(
                            state.getParameter().getParameterName(),
                            state.getParameter().getParameterValues()
                    ))
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

            Parameter parameter =
                    new Parameter.Default(defaultValues);

            parameter.addBounds(new Parameter.DefaultBounds(
                    Double.POSITIVE_INFINITY,
                    0.0,
                    dimension
            ));

            BeastXRealVectorParam<org.phylospec.domain.PositiveReal> defaultState =
                    new BeastXRealVectorParam<>(
                            parameter,
                            org.phylospec.domain.PositiveReal.INSTANCE
                    );

            return new BoundDistribution<>(
                    base.distribution,
                    defaultState,
                    state -> base.distribution.addData(new Attribute.Default<>(
                            state.getParameter().getParameterName(),
                            state.getParameter().getParameterValues()
                    ))
            );
        }
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