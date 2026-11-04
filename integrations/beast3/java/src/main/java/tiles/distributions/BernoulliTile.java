package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.operator.BitFlipOperator;
import beast.base.spec.inference.parameter.BoolScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BernoulliDistribution;
import tiling.BoundDistribution;

public class BernoulliTile
        extends GeneratorTile<BoundDistribution<BoolScalarParam, BernoulliDistribution>, BEASTState> {

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> pInput = new GeneratorTileInput<>("p");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Bernoulli";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<BoolScalarParam, BernoulliDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends Real> probability = pInput.apply(state, indexVariables);
        BernoulliDistribution distribution = new BernoulliDistribution();
        state.setInput(distribution, distribution.pInput, probability);

        boolean initialValue;
        try {
            initialValue = distribution.mostLikelyValue();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid Bernoulli probability.", exception.getMessage());
        }
        BoolScalarParam defaultState = new BoolScalarParam(initialValue);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                value -> state.setInput(distribution, distribution.paramInput, value),
                (value, graph) -> {
                    BitFlipOperator operator = new BitFlipOperator();
                    graph.setInput(operator, operator.parameterInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
