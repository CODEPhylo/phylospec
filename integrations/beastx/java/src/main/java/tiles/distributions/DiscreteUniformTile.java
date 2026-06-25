package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.DiscreteUniformDistribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.ParameterAttribute;

import java.util.IdentityHashMap;

public class DiscreteUniformTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<Int>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteUniform";
    }

    GeneratorTileInput<IntScalar<Int>, BeastXState> lowerInput =
            new GeneratorTileInput<>("lower");

    GeneratorTileInput<IntScalar<Int>, BeastXState> upperInput =
            new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<BeastXIntScalarParam<Int>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        IntScalar<Int> lower =
                this.lowerInput.apply(beastState, indexVariables);

        IntScalar<Int> upper =
                this.upperInput.apply(beastState, indexVariables);

        if (lower.get() > upper.get()) {
            throw new IllegalArgumentException("DiscreteUniform lower must be less than or equal to upper.");
        }

        DiscreteUniformDistribution distribution =
                new DiscreteUniformDistribution(lower.get(), upper.get());

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distribution);

        Parameter.Default defaultParameter =
                new Parameter.Default((double) lower.get());

        defaultParameter.addBounds(
                (double) lower.get(),
                (double) upper.get()
        );

        BeastXIntScalarParam<Int> defaultState =
                new BeastXIntScalarParam<>(defaultParameter, Int.INSTANCE);

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new ParameterAttribute(state.getParameter()))
        );
    }
}