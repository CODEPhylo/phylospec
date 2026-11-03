package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class LogNormalTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, LogNormal>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> logMeanInput = new GeneratorTileInput<>("logMean");
    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> logSdInput = new GeneratorTileInput<>("logSd");

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, LogNormal> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends Real> logMean = this.logMeanInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> logSd = this.logSdInput.apply(beastState, indexVariables);

        LogNormal distribution = new LogNormal();
        // BEAST only reads M through this input. Preserve the original object's
        // domain and live dependency while keeping the log-space parameterization.
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeLogMean = (RealScalar<Real>) logMean;
        beastState.setInput(distribution, distribution.MParameterInput, nativeLogMean);
        beastState.setInput(distribution, distribution.SParameterInput, logSd);

        RealScalarParam<PositiveReal> defaultState =
                new RealScalarParam<>(initialValue(logMean.get()), PositiveReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                OperatorSelector::getDefaultOperators);
    }

    /** Chooses the prior median as a starting value, within the positive finite double range. */
    static double initialValue(double logMedian) {
        // Only the initial value is bounded for numerical representability; the prior is unchanged.
        return Math.max(Double.MIN_VALUE, Math.min(Double.MAX_VALUE, Math.exp(logMedian)));
    }
}
