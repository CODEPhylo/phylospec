package tiles.distributions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Vector;
import beastconfig.BEASTState;
import beastlabs.math.distributions.WeightedDirichlet;
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

/** Binds the BEASTLabs weighted Dirichlet and its weighted-mean-preserving operator. */
public final class WeightedDirichletTile
        extends GeneratorTile<BoundDistribution<RealVectorParam<PositiveReal>, WeightedDirichlet>, BEASTState> {
    private static final Set<Stochasticity> FIXED = Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);

    GeneratorTileInput<RealVector<? extends PositiveReal>, BEASTState> concentrationInput =
            new GeneratorTileInput<>("concentration");
    GeneratorTileInput<Vector<?, ? extends Number>, BEASTState> weightsInput =
            new GeneratorTileInput<>("weights", FIXED);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> meanInput =
            new GeneratorTileInput<>("mean", false, Set.of(Stochasticity.CONSTANT));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "WeightedDirichlet";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealVectorParam<PositiveReal>, WeightedDirichlet> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var weights = weightsInput.apply(state, indices).getElements().stream()
                .mapToDouble(Number::doubleValue)
                .toArray();
        return bind(state, concentrationInput.apply(state, indices), weights, meanInput.apply(state, indices));
    }

    /** Indexed data declarations such as L[i] = numSites(codon[i]) produce a list of integer scalars. */
    public static final class IntegerScalars
            extends GeneratorTile<BoundDistribution<RealVectorParam<PositiveReal>, WeightedDirichlet>, BEASTState> {
        GeneratorTileInput<RealVector<? extends PositiveReal>, BEASTState> concentrationInput =
                new GeneratorTileInput<>("concentration");
        GeneratorTileInput<List<? extends IntScalar<? extends Int>>, BEASTState> weightsInput =
                new GeneratorTileInput<>("weights", FIXED);
        GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> meanInput =
                new GeneratorTileInput<>("mean", false, Set.of(Stochasticity.CONSTANT));

        @Override
        public String getPhyloSpecGeneratorName() {
            return "WeightedDirichlet";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.distributions");
        }

        @Override
        public BoundDistribution<RealVectorParam<PositiveReal>, WeightedDirichlet> applyTile(
                BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            var weights = weightsInput.apply(state, indices).stream()
                    .mapToDouble(IntScalar::get)
                    .toArray();
            return bind(state, concentrationInput.apply(state, indices), weights, meanInput.apply(state, indices));
        }
    }

    private static BoundDistribution<RealVectorParam<PositiveReal>, WeightedDirichlet> bind(
            BEASTState state,
            RealVector<? extends PositiveReal> concentration,
            double[] weights,
            RealScalar<? extends PositiveReal> suppliedMean) {
        double mean = suppliedMean == null ? 1.0 : suppliedMean.get();
        int size = concentration.size();
        if (size < 1 || size != weights.length || !Double.isFinite(mean) || mean <= 0.0) {
            throw invalid(
                    "WeightedDirichlet requires equal nonempty concentration/weights dimensions and a finite positive mean.");
        }
        int[] integerWeights = new int[size];
        long total = 0;
        for (int i = 0; i < size; i++) {
            double alpha = concentration.get(i);
            double weight = weights[i];
            if (!Double.isFinite(alpha) || alpha <= 0.0) {
                throw invalid("WeightedDirichlet concentration entries must be finite and positive.");
            }
            // BEASTLabs accepts real weights, but its native exchange operator uses integers.
            if (!Double.isFinite(weight)
                    || weight <= 0.0
                    || weight != Math.rint(weight)
                    || weight > Integer.MAX_VALUE) {
                throw invalid(
                        "This BEAST binding requires fixed positive integer weights for its DeltaExchangeOperator.");
            }
            integerWeights[i] = (int) weight;
            total += integerWeights[i];
        }
        if (total > Integer.MAX_VALUE) {
            throw invalid("The sum of weights exceeds the BEAST exchange operator's integer range.");
        }
        double[] initial = new double[size];
        Arrays.fill(initial, mean);
        var parameter = new RealVectorParam<>(initial, PositiveReal.INSTANCE);
        var distribution = new WeightedDirichlet();
        var densityWeights = new RealVectorParam<>(weights, PositiveReal.INSTANCE);
        densityWeights.isEstimatedInput.setValue(false, densityWeights);
        state.setInput(distribution, distribution.alphaInput, concentration);
        state.setInput(distribution, distribution.weightsInput, densityWeights);
        state.setInput(distribution, distribution.meanInput, mean);
        final long weightSum = total;
        return new BoundDistribution<>(
                distribution,
                parameter,
                value -> {
                    if (value.size() != size)
                        throw invalid("WeightedDirichlet value dimension must match its weights.");
                    double sum = 0.0;
                    for (int i = 0; i < size; i++) {
                        if (!Double.isFinite(value.get(i)) || value.get(i) <= 0.0) {
                            throw invalid("WeightedDirichlet values must be finite and positive.");
                        }
                        sum += value.get(i) * weights[i];
                    }
                    if (!Double.isFinite(sum) || Math.abs(sum / weightSum - mean) > 1e-6) {
                        throw invalid("WeightedDirichlet values must have the declared weighted mean.");
                    }
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    if (size == 1) return List.of();
                    var operator = new DeltaExchangeOperator();
                    graph.setInput(operator, operator.rvparameterInput, value);
                    var fixedWeights = new IntVectorParam<>(integerWeights, PositiveInt.INSTANCE);
                    fixedWeights.isEstimatedInput.setValue(false, fixedWeights);
                    graph.setInput(operator, operator.parameterWeightsInput, fixedWeights);
                    graph.setInput(operator, operator.deltaInput, 1.0 / size);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }

    private static TileApplicationError invalid(String message) {
        return new TileApplicationError(
                message,
                "Use positive concentrations, fixed positive integer weights and a fixed positive mean (default 1).");
    }
}
