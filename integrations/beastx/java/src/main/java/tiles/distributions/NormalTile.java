package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.NormalDistributionModel;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class NormalTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Normal";
    }

    GeneratorTileInput<RealScalar<Real>, BeastXState> meanInput =
            new GeneratorTileInput<>("mean");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> sdInput =
            new GeneratorTileInput<>("sd");

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<Real> mean =
                this.meanInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> sd =
                this.sdInput.apply(beastState, indexVariables);

        Parameter meanParameter = new Parameter.Default(mean.get());
        Parameter sdParameter = new Parameter.Default(sd.get());

        NormalDistributionModel distributionModel =
                new NormalDistributionModel(meanParameter, sdParameter);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(0.1, Real.INSTANCE);

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new Attribute.Default<>(
                        state.getParameter().getParameterName(),
                        new double[] {state.getParameter().getParameterValue(0)}
                ))
        );
    }
}

