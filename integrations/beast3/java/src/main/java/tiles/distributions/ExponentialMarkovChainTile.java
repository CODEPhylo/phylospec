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

/** Fixed-length exponential chain, retaining the core's Real input and output types. */
public class ExponentialMarkovChainTile
        extends GeneratorTile<
                BoundDistribution<RealVectorParam<Real>, LiveExponentialMarkovChainDistribution>, BEASTState> {

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
    public BoundDistribution<RealVectorParam<Real>, LiveExponentialMarkovChainDistribution> applyTile(
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
        RealVectorParam<Real> chain;
        if (size == 0) {
            // The native constructor assumes at least one value. Configure just
            // this empty instance explicitly, without changing other vector inputs.
            chain = new RealVectorParam<>() {
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
            chain.valuesInput.setRule(Input.Validate.OPTIONAL);
            chain.initAndValidate();
        } else {
            chain = new RealVectorParam<>(values, Real.INSTANCE);
        }

        LiveExponentialMarkovChainDistribution distribution = new LiveExponentialMarkovChainDistribution();
        // Native inputs only read these values. Keep their original objects and
        // domains; the distribution checks their exponential support on every evaluation.
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
                    @SuppressWarnings("unchecked")
                    RealVector<PositiveReal> nativeValues = (RealVector<PositiveReal>) (RealVector<?>) value;
                    state.setInput(distribution, distribution.paramInput, nativeValues);
                },
                (value, graph) -> value.size() == 0 ? List.of() : OperatorSelector.getDefaultOperators(value, graph));
    }
}
