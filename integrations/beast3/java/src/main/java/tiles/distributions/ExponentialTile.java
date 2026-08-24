package tiles.distributions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;

public class ExponentialTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>, BEASTState> rateInput = new GeneratorTileInput<>(
            "rate",
            // PhyloSpec uses a rate parameterization, but BEAST uses a mean parameterization
            // this means that we have to transform the input, which would have an influence on the density of a RV
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);
        RealScalarParam<PositiveReal> mean = new RealScalarParam<>(1.0 / rate.get(), PositiveReal.INSTANCE);

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
