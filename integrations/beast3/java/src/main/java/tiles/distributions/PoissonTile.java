package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class PoissonTile extends GeneratorTile<BoundDistribution<IntScalarParam<NonNegativeInt>, Poisson>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Poisson";
    }

    GeneratorTileInput<RealScalar<NonNegativeReal>> rateInput = new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<IntScalarParam<NonNegativeInt>, Poisson> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<NonNegativeReal> rate = this.rateInput.apply(beastState, indexVariables);

        Poisson distribution = new Poisson();
        beastState.setInput(distribution, distribution.lambdaInput, rate);

        IntScalarParam<NonNegativeInt> defaultState = new IntScalarParam<>(0, NonNegativeInt.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
