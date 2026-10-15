package tiles.distributions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

/** Keeps a conditional exponential mean connected to its sampled parent. */
public final class ExponentialMeanTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, Exponential>, BEASTState> {
    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> meanInput = new GeneratorTileInput<>("mean");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, Exponential> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        Exponential distribution = new Exponential();
        state.setInput(distribution, distribution.meanInput, meanInput.apply(state, indices));
        return new BoundDistribution<>(
                distribution,
                new RealScalarParam<>(0.5, PositiveReal.INSTANCE),
                value -> state.setInput(distribution, distribution.paramInput, nonNegativeView(value)),
                OperatorSelector::getDefaultOperators);
    }

    @SuppressWarnings("unchecked")
    private static RealScalar<NonNegativeReal> nonNegativeView(RealScalar<PositiveReal> value) {
        // Exponential only reads this scalar. PositiveReal is a subset of NonNegativeReal;
        // retain the state node and its stricter domain, rather than copying its current value.
        return (RealScalar<NonNegativeReal>) (RealScalar<?>) value;
    }
}
