package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class BetaTile extends GeneratorTile<BoundDistribution<RealScalarParam<UnitInterval>, Beta>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Beta";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> alphaInput = new GeneratorTileInput<>("alpha");
    GeneratorTileInput<RealScalar<PositiveReal>> betaInput = new GeneratorTileInput<>("beta");

    @Override
    public BoundDistribution<RealScalarParam<UnitInterval>, Beta> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> alpha = this.alphaInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> beta = this.betaInput.apply(beastState, indexVariables);

        Beta distribution = new Beta();
        beastState.setInput(distribution, distribution.alphaInput, alpha);
        beastState.setInput(distribution, distribution.betaInput, beta);

        RealScalarParam<UnitInterval> defaultState = new RealScalarParam<>(0.5, UnitInterval.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
