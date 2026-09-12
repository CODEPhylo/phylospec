package tiles.popfunc;

import beast.base.core.Input;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import operators.popfunc.ModelIndicatorOperator;
import tiling.BoundDistribution;

/** Builds the bounded prior and proposal required by PopFunc model selection. */
public final class ModelIndicatorTile
        extends GeneratorTile<
                BoundDistribution<IntScalarParam<NonNegativeInt>, IntUniform>, BEASTState> {

    private final GeneratorTileInput<List<PopulationFunction>, BEASTState> modelsInput =
            new GeneratorTileInput<>("models");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "modelIndicator";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("popfunc.distributions");
    }

    @Override
    protected BoundDistribution<IntScalarParam<NonNegativeInt>, IntUniform> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int modelCount = modelsInput.apply(state, indexVariables).size();
        if (modelCount < 1) {
            throw new IllegalArgumentException("Model selection requires at least one model.");
        }

        IntUniform prior = new IntUniform();
        state.setInput(prior, prior.lowerInput, new IntScalarParam<>(0, Int.INSTANCE));
        state.setInput(prior, prior.upperInput, new IntScalarParam<>(modelCount - 1, Int.INSTANCE));

        IntScalarParam<NonNegativeInt> indicator =
                new IntScalarParam<>(0, NonNegativeInt.INSTANCE);

        return new BoundDistribution<>(
                prior,
                indicator,
                value -> setParameter(state, prior, value),
                (value, beastState) -> {
                    ModelIndicatorOperator operator = new ModelIndicatorOperator();
                    beastState.setInput(operator, operator.indicatorInput, value);
                    beastState.setInput(operator, operator.modelCountInput, modelCount);
                    beastState.setInput(operator, operator.m_pWeight, 1.0);
                    return List.of(operator);
                });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setParameter(
            BEASTState state, IntUniform prior, IntScalarParam<NonNegativeInt> value) {
        // IntUniform accepts every integer domain at runtime, but its Java signature is invariant.
        state.setInput(prior, (Input) prior.paramInput, value);
    }
}
