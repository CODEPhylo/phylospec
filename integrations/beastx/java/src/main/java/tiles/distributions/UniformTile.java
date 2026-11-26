package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXParameters;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class UniformTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Uniform";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> lowerInput =
            new RealArgumentInput("lower");

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> upperInput =
            new RealArgumentInput("upper");

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends Real> lower =
                this.lowerInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> upper =
                this.upperInput.apply(beastState, indexVariables);

        if (lower.get() > upper.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Uniform lower bound must not be greater than upper bound.",
                    "Use lower <= upper.",
                    List.of("Uniform(lower=0.0, upper=1.0)")
            );
        }

        Parameter lowerParameter = BeastXParameters.toParameter(lower);
        Parameter upperParameter = BeastXParameters.toParameter(upper);

        UniformDistributionModel distributionModel =
                new UniformDistributionModel(lowerParameter, upperParameter);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        double defaultValue = (lower.get() + upper.get()) / 2.0;

        // A sampled limit is left open, as in the XML, which can only store fixed bounds. The Uniform
        // density still rejects values outside the current limits, and in memory and in XML the
        // operators see the same bounds.
        double lowerBound = beastState.stateNodes.containsKey(lowerParameter)
                ? Double.NEGATIVE_INFINITY
                : lower.get();
        double upperBound = beastState.stateNodes.containsKey(upperParameter)
                ? Double.POSITIVE_INFINITY
                : upper.get();

        Parameter.Default defaultParameter = new Parameter.Default(defaultValue);
        defaultParameter.addBounds(new Parameter.DefaultBounds(upperBound, lowerBound, 1));

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(defaultParameter, Real.INSTANCE);

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(state.getParameter())
        );
    }
}
