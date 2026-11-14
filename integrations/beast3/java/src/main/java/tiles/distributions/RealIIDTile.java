package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import tiling.BoundDistribution;

/** Fixed-length IID binding for the native Normal and Cauchy laws. */
public class RealIIDTile
        extends GeneratorTile<
                BoundDistribution<RealVectorParam<Real>, IID<RealVectorParam<Real>, RealScalar<Real>, Double>>,
                BEASTState> {

    GeneratorTileInput<
                    BoundDistribution<
                            ? extends RealScalarParam<Real>,
                            ? extends ScalarDistribution<? extends RealScalar<Real>, Double>>,
                    BEASTState>
            baseInput = new GeneratorTileInput<>("base") {
                @Override
                public Set<Tile<?, BEASTState>> getCompatibleInputTiles(
                        AstNode node,
                        Map<AstNode, Set<Tile<?, BEASTState>>> candidates,
                        StochasticityResolver stochasticityResolver)
                        throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
                    Set<Tile<?, BEASTState>> compatible =
                            super.getCompatibleInputTiles(node, candidates, stochasticityResolver);
                    TypeToken<?> normal = new TypeToken<BoundDistribution<RealScalarParam<Real>, Normal>>() {};
                    TypeToken<?> cauchy = new TypeToken<BoundDistribution<RealScalarParam<Real>, Cauchy>>() {};
                    compatible.removeIf(tile -> !normal.isAssignableFrom(tile.getTypeToken())
                            && !cauchy.isAssignableFrom(tile.getTypeToken()));
                    return compatible;
                }
            };

    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "IID";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealVectorParam<Real>, IID<RealVectorParam<Real>, RealScalar<Real>, Double>> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = baseInput.apply(state, indices);
        int size = numInput.apply(state, indices).get();
        if (size < 1) {
            throw new TileApplicationError(
                    getRootNode(),
                    "IID requires a positive number of elements.",
                    "Provide a fixed positive integer for num.");
        }

        double initial = base.getStateNode().get();
        if (!Double.isFinite(initial)) {
            throw new TileApplicationError(
                    getRootNode(),
                    "IID requires a finite initial value from its base distribution.",
                    "Use a supported Normal or Cauchy base distribution.");
        }
        double[] values = new double[size];
        Arrays.fill(values, initial);
        RealVectorParam<Real> parameter = new RealVectorParam<>(values, Real.INSTANCE);

        // Reuse the scalar law and its live inputs without registering a scalar draw.
        var scalarLaw = base.consumeAsBase();
        IID<RealVectorParam<Real>, RealScalar<Real>, Double> iid = new IID<>();
        state.setInput(iid, iid.distInput, scalarLaw);

        return new BoundDistribution<>(
                iid,
                parameter,
                value -> {
                    if (value.size() != size) {
                        throw new TileApplicationError(
                                getRootNode(),
                                "The IID vector length does not match num.",
                                "Provide exactly " + size + " elements.");
                    }
                    state.setInput(iid, iid.paramInput, value);
                },
                (value, graph) -> {
                    // Additive proposals allow every coordinate to leave zero and cross signs.
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    graph.setInput(operator, operator.parameterInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
