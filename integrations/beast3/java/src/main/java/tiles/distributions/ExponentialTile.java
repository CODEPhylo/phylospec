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
import tiling.ReciprocalValue;

public class ExponentialTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> rateInput = new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);

        // Convert the conditional distribution's parameterization without
        // replacing the sampled rate or changing its prior.
        ReciprocalValue mean = new ReciprocalValue();
        beastState.setInput(mean, mean.rateInput, rate);

        Exponential distribution = new Exponential();
        beastState.setInput(distribution, distribution.meanInput, mean);

        RealScalarParam<NonNegativeReal> defaultState = new RealScalarParam<>(1.0, NonNegativeReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                OperatorSelector::getDefaultOperators);
    }
}
