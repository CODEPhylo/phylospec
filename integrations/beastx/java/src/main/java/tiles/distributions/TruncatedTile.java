package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BeastXTruncatedLikelihood;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.Set;

public class TruncatedTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, BeastXTruncatedLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Truncated";
    }

    GeneratorTileInput<
            BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
            BeastXState
            > baseInput =
            new GeneratorTileInput<>("base");

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> lowerInput =
            new GeneratorTileInput<>("lower", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> upperInput =
            new GeneratorTileInput<>("upper", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, BeastXTruncatedLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> base =
                this.baseInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> lower =
                this.lowerInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> upper =
                this.upperInput.apply(beastState, indexVariables);

        double lowerValue =
                lower == null ? Double.NEGATIVE_INFINITY : lower.get();

        double upperValue =
                upper == null ? Double.POSITIVE_INFINITY : upper.get();

        if (lowerValue > upperValue) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Truncated lower bound must not be greater than upper bound.",
                    "Use lower <= upper.",
                    java.util.List.of("Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=-1.0, upper=1.0)")
            );
        }

        BeastXTruncatedLikelihood likelihood =
                new BeastXTruncatedLikelihood(
                        base.distribution,
                        lowerValue,
                        upperValue
                );

        double initialValue =
                boundedInitialValue(
                        base.stateNode.getParameter().getParameterValue(0),
                        lowerValue,
                        upperValue
                );

        Parameter.Default defaultParameter =
                new Parameter.Default(initialValue);

        defaultParameter.addBounds(new Parameter.DefaultBounds(
                upperValue,
                lowerValue,
                1
        ));

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(
                        defaultParameter,
                        Real.INSTANCE
                );

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(state.getParameter())
        );
    }

    private static double boundedInitialValue(
            double candidate,
            double lower,
            double upper
    ) {
        // BEAST X gives a value on a bound zero density, so the start must be strictly inside.
        if (candidate > lower && candidate < upper) {
            return candidate;
        }

        if (Double.isFinite(lower) && Double.isFinite(upper)) {
            return (lower + upper) / 2.0;
        }

        if (Double.isFinite(lower)) {
            return lower + 1.0;
        }

        if (Double.isFinite(upper)) {
            return upper - 1.0;
        }

        return candidate;
    }
}