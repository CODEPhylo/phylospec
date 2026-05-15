package tiles.distributions;

import dr.inference.distribution.BetaDistributionModel;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class BetaTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<UnitInterval>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Beta";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> alphaInput =
            new GeneratorTileInput<>("alpha");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> betaInput =
            new GeneratorTileInput<>("beta");

    @Override
    public BoundDistribution<BeastXRealScalarParam<UnitInterval>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> alpha =
                this.alphaInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> beta =
                this.betaInput.apply(beastState, indexVariables);

        Parameter alphaParameter = new Parameter.Default(alpha.get());
        Parameter betaParameter = new Parameter.Default(beta.get());

        BetaDistributionModel distributionModel =
                new BetaDistributionModel(alphaParameter, betaParameter);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        Parameter.Default defaultParameter = new Parameter.Default(0.5);
        defaultParameter.addBounds(0.0, 1.0);

        BeastXRealScalarParam<UnitInterval> defaultState =
                new BeastXRealScalarParam<>(defaultParameter, UnitInterval.INSTANCE);

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
