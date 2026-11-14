package tiles.distributions;

import dr.inference.distribution.DirichletDistributionModel;
import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.distributions.DirichletDistribution;
import dr.math.distributions.MultivariateDistribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealVector;
import tiling.params.BeastXParameters;
import tiling.params.BeastXSimplexParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Dirichlet with a fixed concentration vector.
 *
 * <p>Sampled concentrations are handled by {@link Live}.</p>
 */
public class DirichletTile extends GeneratorTile<
        BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Dirichlet";
    }

    GeneratorTileInput<RealVector<PositiveReal>, BeastXState> concentrationInput =
            new GeneratorTileInput<>(
                    "concentration",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<PositiveReal> concentration =
                this.concentrationInput.apply(beastState, indexVariables);

        DirichletDistribution distribution =
                new DirichletDistribution(concentration.getDoubleArray(), false);

        return bind(distribution, concentration);
    }

    /**
     * Dirichlet with a sampled concentration vector. The density reads the live
     * concentration parameter, so it follows MCMC proposals.
     */
    public static class Live extends GeneratorTile<
            BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood>,
            BeastXState
            > {

        @Override
        public String getPhyloSpecGeneratorName() {
            return "Dirichlet";
        }

        GeneratorTileInput<RealVector<PositiveReal>, BeastXState> concentrationInput =
                new GeneratorTileInput<>(
                        "concentration",
                        Set.of(Stochasticity.STOCHASTIC)
                );

        @Override
        public BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood> applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            RealVector<PositiveReal> concentration =
                    this.concentrationInput.apply(beastState, indexVariables);

            // BEAST X uses concentration_i = counts_i * dispersion; a fixed dispersion of 1
            // makes the counts parameter the concentration vector itself.
            DirichletDistributionModel distribution =
                    new DirichletDistributionModel(
                            BeastXParameters.toParameter(concentration),
                            new Parameter.Default(1.0),
                            false
                    );

            return bind(distribution, concentration);
        }
    }

    private static BoundDistribution<BeastXSimplexParam, MultivariateDistributionLikelihood> bind(
            MultivariateDistribution distribution,
            RealVector<PositiveReal> concentration
    ) {
        int dim = Math.toIntExact(concentration.size());

        if (dim == 0) {
            throw new IllegalArgumentException("Dirichlet concentration vector cannot be empty.");
        }

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
}
