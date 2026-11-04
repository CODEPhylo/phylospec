package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;
import tiling.LiveTruncatedDistribution;

/** Continuous scalar truncation with fixed bounds and live base parameters. */
public class TruncatedTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, LiveTruncatedDistribution>, BEASTState> {

    GeneratorTileInput<
                    BoundDistribution<
                            ? extends RealScalarParam<? extends Real>,
                            ? extends ScalarDistribution<? extends RealScalar<? extends Real>, Double>>,
                    BEASTState>
            baseInput = new GeneratorTileInput<>("base");

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> lowerInput =
            new GeneratorTileInput<>("lower", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> upperInput =
            new GeneratorTileInput<>("upper", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Truncated";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealScalarParam<Real>, LiveTruncatedDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = baseInput.apply(state, indices);
        RealScalar<? extends Real> lower = lowerInput.apply(state, indices);
        RealScalar<? extends Real> upper = upperInput.apply(state, indices);

        // Consume only the law. The base must not acquire a separate scalar draw or prior.
        @SuppressWarnings("unchecked")
        ScalarDistribution<RealScalar<Real>, Double> nativeBase =
                (ScalarDistribution<RealScalar<Real>, Double>) base.consumeAsBase();
        LiveTruncatedDistribution distribution = new LiveTruncatedDistribution();
        state.setInput(distribution, distribution.distributionInput, nativeBase);
        if (lower != null) {
            @SuppressWarnings("unchecked")
            RealScalar<Real> nativeLower = (RealScalar<Real>) lower;
            state.setInput(distribution, distribution.lowerInput, nativeLower);
        }
        if (upper != null) {
            @SuppressWarnings("unchecked")
            RealScalar<Real> nativeUpper = (RealScalar<Real>) upper;
            state.setInput(distribution, distribution.upperInput, nativeUpper);
        }

        double initialValue = base.getStateNode().get();
        try {
            distribution.validateInterval();
            // Preserve a usable base initializer. Otherwise choose the conditional median,
            // without calling the native truncated mean's numerical integration.
            if (!Double.isFinite(initialValue) || !Double.isFinite(distribution.logDensity(initialValue))) {
                initialValue = distribution.inverseCumulativeProbability(0.5);
            }
            if (!Double.isFinite(initialValue) || !Double.isFinite(distribution.logDensity(initialValue))) {
                throw new IllegalArgumentException("The truncated distribution has no usable finite initial value.");
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new TileApplicationError(
                    getRootNode(), "Cannot initialize this continuous Truncated distribution.", exception.getMessage());
        }

        RealScalarParam<Real> parameter = new RealScalarParam<>(initialValue, Real.INSTANCE);
        return new BoundDistribution<>(
                distribution,
                parameter,
                value -> {
                    // Out-of-support observations remain unchanged and receive logP = -Infinity.
                    state.initBEASTObject(value);
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    graph.setInput(operator, operator.scalarInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
