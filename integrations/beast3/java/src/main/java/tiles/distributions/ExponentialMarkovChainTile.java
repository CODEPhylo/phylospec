package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.MarkovChainDistribution;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;

/**
 * {@code ExponentialMarkovChain(initialMean, numValues)}: x[0] ~ Exp(mean = initialMean) and
 * x[i] ~ Exp(mean = x[i-1]). Maps to BEAST's {@link MarkovChainDistribution} with shape 1 (a gamma
 * with shape 1 is an exponential) and an initial mean as the boundary condition.
 */
public class ExponentialMarkovChainTile
        extends GeneratorTile<BoundDistribution<RealVectorParam<PositiveReal>, MarkovChainDistribution>, BEASTState> {

    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> initialMeanInput = new GeneratorTileInput<>("initialMean");
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numValuesInput =
            new GeneratorTileInput<>("numValues", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "ExponentialMarkovChain";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealVectorParam<PositiveReal>, MarkovChainDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        RealScalar<PositiveReal> initialMean = this.initialMeanInput.apply(state, indices);
        int size = this.numValuesInput.apply(state, indices).get();

        if (size < 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "ExponentialMarkovChain requires a positive number of values.",
                    "Provide a fixed positive integer for numValues.");
        }

        // default state: every value equal to the current initial mean (the chain's expectation)
        double initial = initialMean.get();
        if (!Double.isFinite(initial) || initial <= 0.0) {
            initial = 1.0;
        }
        double[] values = new double[size];
        Arrays.fill(values, initial);
        RealVectorParam<PositiveReal> chain = new RealVectorParam<>(values, PositiveReal.INSTANCE);

        MarkovChainDistribution distribution = new MarkovChainDistribution();
        state.setInput(distribution, distribution.initialMeanInput, initialMean);
        state.setInput(distribution, distribution.shapeInput, 1.0);

        return new BoundDistribution<>(
                distribution,
                chain,
                value -> {
                    if (value.size() != size) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The chain length does not match numValues.",
                                "Provide exactly " + size + " values.");
                    }
                    state.setInput(distribution, distribution.paramInput, value);
                },
                OperatorSelector::getDefaultOperators);
    }
}
