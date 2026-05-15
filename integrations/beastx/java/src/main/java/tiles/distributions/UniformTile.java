package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class UniformTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Uniform";
    }

    GeneratorTileInput<RealScalar<Real>, BeastXState> lowerInput =
            new GeneratorTileInput<>("lower");

    GeneratorTileInput<RealScalar<Real>, BeastXState> upperInput =
            new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<Real> lower =
                this.lowerInput.apply(beastState, indexVariables);

        RealScalar<Real> upper =
                this.upperInput.apply(beastState, indexVariables);

        Parameter lowerParameter = new Parameter.Default(lower.get());
        Parameter upperParameter = new Parameter.Default(upper.get());

        UniformDistributionModel distributionModel =
                new UniformDistributionModel(lowerParameter, upperParameter);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        double defaultValue = (lower.get() + upper.get()) / 2.0;

        Parameter.Default defaultParameter = new Parameter.Default(defaultValue);
        defaultParameter.addBounds(lower.get(), upper.get());

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(defaultParameter, Real.INSTANCE);

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
