package tiles.distributions;

import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.math.distributions.DirichletDistribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealVector;
import tiling.params.BeastXSimplexParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.OptionalLong;
import java.util.Arrays;
import java.util.IdentityHashMap;

public class DirichletTile extends GeneratorTile<
        BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Dirichlet";
    }

    GeneratorTileInput<RealVector<PositiveReal>, BeastXState> concentrationInput =
            new GeneratorTileInput<>("concentration");

    @Override
    public BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<PositiveReal> concentration =
                this.concentrationInput.apply(beastState, indexVariables);

        int dim = Math.toIntExact(concentration.size());

        if (dim == 0) {
            throw new IllegalArgumentException("Dirichlet concentration vector cannot be empty.");
        }

        double[] concentrationValues = concentration.getDoubleArray();

        DirichletDistribution distribution =
                new DirichletDistribution(concentrationValues, false);

        MultivariateDistributionLikelihood likelihood =
                new MultivariateDistributionLikelihood(distribution);

        double[] defaultValues = new double[dim];
        Arrays.fill(defaultValues, 1.0 / dim);

        BeastXSimplexParam defaultState =
                new BeastXSimplexParam(defaultValues);

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(state.getParameter())
        );
    }

    @Override
    public OptionalLong getFixedOutputSize() {
        return this.concentrationInput.getTile() == null
                ? OptionalLong.empty()
                : this.concentrationInput.getTile().getFixedOutputSize();
    }
}
