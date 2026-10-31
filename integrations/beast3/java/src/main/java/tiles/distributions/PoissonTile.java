package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class PoissonTile extends GeneratorTile<BoundDistribution<IntScalarParam<NonNegativeInt>, Poisson>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Poisson";
    }

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BEASTState> rateInput = new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<IntScalarParam<NonNegativeInt>, Poisson> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends NonNegativeReal> rate = this.rateInput.apply(beastState, indexVariables);

        Poisson distribution = new Poisson();

        // BEAST only reads lambda through this input. Keep the original object,
        // including its domain and live dependency on the sampled state.
        @SuppressWarnings("unchecked")
        RealScalar<NonNegativeReal> nativeRate = (RealScalar<NonNegativeReal>) rate;
        beastState.setInput(distribution, distribution.lambdaInput, nativeRate);

        IntScalarParam<NonNegativeInt> defaultState = new IntScalarParam<>(0, NonNegativeInt.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                OperatorSelector::getDefaultOperators);
    }
}
