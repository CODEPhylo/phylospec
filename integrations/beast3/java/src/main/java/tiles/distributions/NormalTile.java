package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class NormalTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Normal>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Normal";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> meanInput = new GeneratorTileInput<>("mean");
    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> sdInput = new GeneratorTileInput<>("sd");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Normal> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends Real> mean = this.meanInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> sd = this.sdInput.apply(beastState, indexVariables);

        Normal distribution = new Normal();

        // BEAST only reads the mean through this input. Keep the original
        // object, including its domain and live dependency on the sampled state.
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeMean = (RealScalar<Real>) mean;
        beastState.setInput(distribution, distribution.meanInput, nativeMean);

        beastState.setInput(distribution, distribution.sdInput, sd);

        RealScalarParam<Real> defaultState = new RealScalarParam<>(0.1, Real.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                OperatorSelector::getDefaultOperators);
    }
}
