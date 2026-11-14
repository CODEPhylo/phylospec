package tiles.distributions;

import beast.base.inference.Operator;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntSimplexParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.rpn.RPNCalculationResult;
import tiling.BoundDistribution;
import tiling.UniformComposition;

/**
 * {@code RandomComposition(total, numParts)}: a uniformly distributed vector of numParts positive
 * integers summing to total, e.g. the group sizes of a classic Bayesian skyline.
 *
 * <p>The state is an {@link IntSimplexParam} so the sum is part of its type. It is only moved by
 * an integer delta-exchange operator, which keeps the sum fixed; the default integer random walk
 * would break it.
 */
public abstract class RandomCompositionTile
        extends GeneratorTile<BoundDistribution<IntSimplexParam<PositiveInt>, UniformComposition>, BEASTState> {

    /** total given as an integer value, e.g. {@code total=11} or {@code total=n}. */
    public static class Value extends RandomCompositionTile {
        GeneratorTileInput<IntScalarParam<? extends Int>, BEASTState> totalInput =
                new GeneratorTileInput<>("total", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numPartsInput =
                new GeneratorTileInput<>("numParts", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        @Override
        int numParts(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return this.numPartsInput.apply(state, indices).get();
        }

        @Override
        int total(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return this.totalInput.apply(state, indices).get();
        }
    }

    /** total given as constant arithmetic, e.g. {@code total=numTaxa(data) - 1}. */
    public static class Calculated extends RandomCompositionTile {
        GeneratorTileInput<RPNCalculationResult, BEASTState> totalInput =
                new GeneratorTileInput<>("total", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numPartsInput =
                new GeneratorTileInput<>("numParts", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        @Override
        int numParts(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            return this.numPartsInput.apply(state, indices).get();
        }

        @Override
        int total(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
            RPNCalculationResult calculation = this.totalInput.apply(state, indices);
            double value = calculation.evaluateConstant();
            if (value != Math.rint(value)) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "RandomComposition needs an integer total, but it evaluates to " + value + ".",
                        "Use integer arithmetic for total.");
            }
            return (int) value;
        }
    }

    // tile inputs are discovered from the concrete class's own fields, so each subclass declares them
    abstract int total(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices);

    abstract int numParts(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices);

    @Override
    public String getPhyloSpecGeneratorName() {
        return "RandomComposition";
    }

    // the concrete tiles are nested subclasses, so the return type cannot be read from their superclass
    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BoundDistribution<IntSimplexParam<PositiveInt>, UniformComposition>>() {};
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<IntSimplexParam<PositiveInt>, UniformComposition> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        int total = this.total(state, indices);
        int parts = this.numParts(state, indices);

        if (parts < 1 || total < parts) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "RandomComposition needs 1 <= numParts <= total, but got numParts=" + parts + " and total=" + total
                            + ".",
                    "Use fewer parts or a larger total.");
        }

        // default state: split the total as evenly as possible
        int[] values = new int[parts];
        for (int i = 0; i < parts; i++) {
            values[i] = total / parts + (i < total % parts ? 1 : 0);
        }
        IntSimplexParam<PositiveInt> composition = new IntSimplexParam<>(values, PositiveInt.INSTANCE, total);

        UniformComposition prior = new UniformComposition();

        return new BoundDistribution<>(
                prior,
                composition,
                value -> {
                    if (value.size() != parts || value.expectedSum() != total) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The composition does not have " + parts + " parts summing to " + total + ".",
                                "Check numParts and total.");
                    }
                    state.setInput(prior, prior.parameterInput, value);
                },
                RandomCompositionTile::operators);
    }

    static List<Operator> operators(IntSimplexParam<PositiveInt> composition, BEASTState state) {
        if (composition.size() < 2) {
            return List.of(); // a single part is fixed by the total
        }
        DeltaExchangeOperator deltaExchange = new DeltaExchangeOperator();
        state.setInput(deltaExchange, deltaExchange.ivparameterInput, composition);
        state.setInput(deltaExchange, deltaExchange.integerOperatorInput, true);
        state.setInput(deltaExchange, deltaExchange.deltaInput, 1.0);
        state.setInput(deltaExchange, deltaExchange.autoOptimizeiInput, false);
        state.setInput(deltaExchange, deltaExchange.m_pWeight, 6.0);
        return List.of(deltaExchange);
    }
}
