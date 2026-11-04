package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import operators.IntScalarRandomWalkOperator;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;
import tiling.GeometricDistribution;

public class GeometricTile
        extends GeneratorTile<BoundDistribution<IntScalarParam<NonNegativeInt>, GeometricDistribution>, BEASTState> {

    GeneratorTileInput<RealScalar<? extends UnitInterval>, BEASTState> pInput = new GeneratorTileInput<>("p");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Geometric";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<IntScalarParam<NonNegativeInt>, GeometricDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        GeometricDistribution distribution = new GeometricDistribution();
        state.setInput(distribution, distribution.pInput, pInput.apply(state, indexVariables));

        int initialCount;
        try {
            initialCount = distribution.mostLikelyCount();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid geometric inputs.", exception.getMessage());
        }
        IntScalarParam<NonNegativeInt> defaultState = new IntScalarParam<>(initialCount, NonNegativeInt.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                value -> {
                    // Preserve an impossible observation (e.g. count > 0 when p = 1).
                    state.initBEASTObject(value);
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    IntScalarRandomWalkOperator operator = new IntScalarRandomWalkOperator();
                    graph.setInput(operator, operator.parameterInput, value);
                    graph.setInput(operator, operator.m_pWeight, 10.0);
                    return List.of(operator);
                });
    }
}
