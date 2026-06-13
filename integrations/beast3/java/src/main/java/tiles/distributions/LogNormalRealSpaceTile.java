package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class LogNormalRealSpaceTile extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, LogNormal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    GeneratorTileInput<RealScalar<Real>> meanInput = new GeneratorTileInput<>("mean");
    GeneratorTileInput<RealScalar<PositiveReal>> logSdInput = new GeneratorTileInput<>("logSd");

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, LogNormal> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<Real> mean = this.meanInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> logSd = this.logSdInput.apply(beastState, indexVariables);

        LogNormal distribution = new LogNormal();
        beastState.setInput(distribution, distribution.MParameterInput, mean);
        beastState.setInput(distribution, distribution.SParameterInput, logSd);
        beastState.setInput(distribution, distribution.hasMeanInRealSpaceInput, true);

        RealScalarParam<PositiveReal> defaultState = new RealScalarParam<>(0.5, PositiveReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
