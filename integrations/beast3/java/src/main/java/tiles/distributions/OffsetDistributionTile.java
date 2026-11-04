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
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;
import tiling.LiveOffsetDistribution;

/** A shifted scalar law; unlike ordinary addition, this creates a distribution. */
public class OffsetDistributionTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, LiveOffsetDistribution>, BEASTState> {

    GeneratorTileInput<
                    BoundDistribution<
                            ? extends RealScalarParam<? extends Real>,
                            ? extends ScalarDistribution<? extends RealScalar<? extends Real>, Double>>,
                    BEASTState>
            baseInput = new GeneratorTileInput<>("base");

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> offsetInput = new GeneratorTileInput<>("offset");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Offset";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealScalarParam<Real>, LiveOffsetDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = baseInput.apply(state, indices);
        RealScalar<? extends Real> offset = offsetInput.apply(state, indices);
        double initialValue = base.getStateNode().get() + offset.get();
        if (!Double.isFinite(initialValue)) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Offset requires a finite initial value.",
                    "The base distribution's initial value plus offset must be finite.");
        }

        // Reuse the unbound law and its dependencies without creating a separate base draw.
        // OffsetReal reads the scalar law and offset; these casts preserve narrower input domains.
        @SuppressWarnings("unchecked")
        ScalarDistribution<RealScalar<Real>, Double> nativeBase =
                (ScalarDistribution<RealScalar<Real>, Double>) base.consumeAsBase();
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeOffset = (RealScalar<Real>) offset;
        LiveOffsetDistribution distribution = new LiveOffsetDistribution();
        state.setInput(distribution, distribution.distributionInput, nativeBase);
        state.setInput(distribution, distribution.offsetInput, nativeOffset);

        RealScalarParam<Real> parameter = new RealScalarParam<>(initialValue, Real.INSTANCE);
        return new BoundDistribution<>(
                distribution,
                parameter,
                value -> {
                    // An impossible observation must retain its value and receive logP = -Infinity.
                    state.initBEASTObject(value);
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    // A translated real-valued law needs a proposal that can leave zero and cross signs.
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    graph.setInput(operator, operator.scalarInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
