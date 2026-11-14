package tiles.distributions;

import beast.base.core.Input;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;
import tiling.LiveExponentialMarkovChainDistribution;

/**
 * Fixed-length exponential chain: x[0] ~ Exp(mean = initialMean) and x[i] ~ Exp(mean = x[i-1]).
 * The values are positive, as in BEAST 3's {@code MarkovChainDistribution}, so the chain can feed
 * positive vector inputs such as skyline population sizes.
 */
public class ExponentialMarkovChainTile
        extends GeneratorTile<
                BoundDistribution<RealVectorParam<PositiveReal>, LiveExponentialMarkovChainDistribution>, BEASTState> {

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> initialMeanInput =
            new GeneratorTileInput<>("initialMean");
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
    public BoundDistribution<RealVectorParam<PositiveReal>, LiveExponentialMarkovChainDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        RealScalar<? extends Real> initialMean = initialMeanInput.apply(state, indices);
        int size = numValuesInput.apply(state, indices).get();
        if (size < 0) {
            throw new TileApplicationError(
                    getRootNode(), "Invalid exponential chain length.", "Provide a fixed non-negative numValues.");
        }
        double initial = initialMean.get();
        if (size > 0 && (!Double.isFinite(initial) || initial <= 0.0)) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Invalid exponential chain initial mean.",
                    "initialMean must be finite and greater than zero for a nonempty chain.");
        }

        double[] values = new double[size];
        Arrays.fill(values, initial);
        RealVectorParam<PositiveReal> chain;
        if (size == 0) {
            // The native constructor assumes at least one value. Configure just
            // this empty instance explicitly, without changing other vector inputs.
            chain = new RealVectorParam<PositiveReal>() {
                @Override
                public void fromXML(String shape, String... storedValues) {
                    // The native state reader splits empty text into one blank value.
                    if ("0".equals(shape) && storedValues.length == 1 && storedValues[0].isBlank()) {
                        super.fromXML(shape, new String[0]);
                    } else {
                        super.fromXML(shape, storedValues);
                    }
                }
            };
            chain.dimensionInput.setValue(0, chain);
            chain.domainTypeInput.setValue(PositiveReal.INSTANCE, chain);
            chain.valuesInput.setRule(Input.Validate.OPTIONAL);
            chain.initAndValidate();
        } else {
            chain = new RealVectorParam<>(values, PositiveReal.INSTANCE);
        }

        LiveExponentialMarkovChainDistribution distribution = new LiveExponentialMarkovChainDistribution();
        // The type checker requires a positive initial mean. A computed mean can still
        // carry a wider native domain, so keep its object and check its value on every evaluation.
        @SuppressWarnings("unchecked")
        RealScalar<PositiveReal> nativeMean = (RealScalar<PositiveReal>) initialMean;
        state.setInput(distribution, distribution.initialMeanInput, nativeMean);
        state.setInput(distribution, distribution.shapeInput, 1.0);

        return new BoundDistribution<>(
                distribution,
                chain,
                value -> {
                    if (value.size() != size) {
                        throw new TileApplicationError(
                                getRootNode(),
                                "The chain length does not match numValues.",
                                "Provide exactly " + size + " values.");
                    }
                    // Observed values may carry a wider native domain; the distribution
                    // checks their exponential support on every evaluation.
                    @SuppressWarnings("unchecked")
                    RealVector<PositiveReal> nativeValues = (RealVector<PositiveReal>) (RealVector<?>) value;
                    state.setInput(distribution, distribution.paramInput, nativeValues);
                },
                (value, graph) -> value.size() == 0 ? List.of() : OperatorSelector.getDefaultOperators(value, graph));
    }
}
