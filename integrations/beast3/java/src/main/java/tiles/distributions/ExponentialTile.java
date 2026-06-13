package tiles.distributions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.Set;

public class ExponentialTile extends GeneratorTile<BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>> rateInput = new GeneratorTileInput<>(
            "rate",
            // PhyloSpec uses a rate parameterization, but BEAST uses a mean parameterization
            // this means that we have to transform the input, which would have an influence on the density of a RV
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);
        RealScalarParam<PositiveReal> mean = new RealScalarParam<>(1.0 / rate.get(), PositiveReal.INSTANCE);

        Exponential distribution = new Exponential();
        beastState.setInput(distribution, distribution.meanInput, mean);

        RealScalarParam<NonNegativeReal> defaultState = new RealScalarParam<>(1.0, NonNegativeReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
