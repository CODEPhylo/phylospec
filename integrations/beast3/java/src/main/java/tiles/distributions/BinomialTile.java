package tiles.distributions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import operators.IntScalarUniformOperator;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BinomialDistribution;
import tiling.BoundDistribution;

public class BinomialTile
        extends GeneratorTile<BoundDistribution<IntScalarParam<NonNegativeInt>, BinomialDistribution>, BEASTState> {

    GeneratorTileInput<IntScalar<? extends Int>, BEASTState> numTrialsInput =
            new GeneratorTileInput<>("numTrials", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<RealScalar<? extends UnitInterval>, BEASTState> pInput = new GeneratorTileInput<>("p");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Binomial";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<IntScalarParam<NonNegativeInt>, BinomialDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int numTrials = numTrialsInput.apply(state, indexVariables).get();
        RealScalar<? extends UnitInterval> probability = pInput.apply(state, indexVariables);
        BinomialDistribution distribution = new BinomialDistribution();
        state.setInput(distribution, distribution.numTrialsInput, numTrials);
        state.setInput(distribution, distribution.pInput, probability);

        int initialCount;
        try {
            initialCount = distribution.mostLikelyCount();
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid binomial inputs.", exception.getMessage());
        }
        IntScalarParam<NonNegativeInt> defaultState = new IntScalarParam<>(initialCount, NonNegativeInt.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                value -> {
                    // An observed count above numTrials must retain its value and have logP = -Infinity.
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
