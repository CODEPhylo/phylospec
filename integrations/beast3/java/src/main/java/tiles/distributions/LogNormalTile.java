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

public class LogNormalTile extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, LogNormal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    GeneratorTileInput<RealScalar<Real>> logMeanInput = new GeneratorTileInput<>("logMean");
    GeneratorTileInput<RealScalar<PositiveReal>> logSdInput = new GeneratorTileInput<>("logSd");

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, LogNormal> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<Real> logMean = this.logMeanInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> logSd = this.logSdInput.apply(beastState, indexVariables);

        LogNormal distribution = new LogNormal();
        beastState.setInput(distribution, distribution.MParameterInput, logMean);
        beastState.setInput(distribution, distribution.SParameterInput, logSd);

        RealScalarParam<PositiveReal> defaultState = new RealScalarParam<>(0.5, PositiveReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
