package tiles.distributions;

import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import operators.IntScalarUniformOperator;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;
import tiling.CategoricalDistribution;

public class CategoricalTile
        extends GeneratorTile<BoundDistribution<IntScalarParam<PositiveInt>, CategoricalDistribution>, BEASTState> {

    GeneratorTileInput<Simplex, BEASTState> probabilitiesInput = new GeneratorTileInput<>("probabilities");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Categorical";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<IntScalarParam<PositiveInt>, CategoricalDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex probabilities = probabilitiesInput.apply(state, indexVariables);
        CategoricalDistribution distribution = new CategoricalDistribution();
        state.setInput(distribution, distribution.probabilitiesInput, probabilities);

        int initialCategory;
        try {
            initialCategory = distribution.mostLikelyCategory();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid categorical probabilities.", exception.getMessage());
        }
        IntScalarParam<PositiveInt> defaultState = new IntScalarParam<>(initialCategory, PositiveInt.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                value -> {
                    // Validate the value's own domain before attaching distribution bounds.
                    // A positive observation outside 1..K has zero probability, not a type error.
                    state.initBEASTObject(value);
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    IntScalarUniformOperator operator = new IntScalarUniformOperator();
                    graph.setInput(operator, operator.parameterInput, value);
                    graph.setInput(operator, operator.m_pWeight, 10.0);
                    return List.of(operator);
                });
    }
}
