package tiles.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.tree.coalescent.ExponentialGrowth;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;

public class ExponentialPopulationGeneratedTileTest {

    @Test
    public void exposesSameInputContractAsHandwrittenTile() {
        ExponentialPopulationTile handwrittenTile = new ExponentialPopulationTile();

        ExponentialPopulationGeneratedTile generatedTile = new ExponentialPopulationGeneratedTile();

        assertEquals(handwrittenTile.getPhyloSpecGeneratorName(), generatedTile.getPhyloSpecGeneratorName());

        assertEquals(handwrittenTile.getTypeToken(), generatedTile.getTypeToken());

        assertEquals(
                List.of("populationSize", "growthRate"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());

        assertEquals(
                List.of(true, true),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.isRequired())
                        .toList());

        assertEquals(
                handwrittenTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken())
                        .toList(),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken())
                        .toList());

        assertEquals(
                List.of("RealScalar<PositiveReal>", "RealScalar<Real>"),
                generatedTile.getGeneratorTileInputs().stream()
                        .map(input -> input.getTypeToken().toString())
                        .toList());
    }

    @Test
    public void setsTheSameBeastInputsAsHandwrittenTile() {
        RealScalarParam<PositiveReal> populationSize = new RealScalarParam<>(100.0, PositiveReal.INSTANCE);

        RealScalarParam<Real> growthRate = new RealScalarParam<>(-0.2, Real.INSTANCE);

        Tile<RealScalar<? extends PositiveReal>, BEASTState> populationSizeTile =
                ExponentialPopulationGeneratedTileTest.<RealScalar<? extends PositiveReal>>valueTile(populationSize);

        Tile<RealScalar<? extends Real>, BEASTState> growthRateTile =
                ExponentialPopulationGeneratedTileTest.<RealScalar<? extends Real>>valueTile(growthRate);

        ExponentialPopulationTile handwrittenTile = new ExponentialPopulationTile();

        ExponentialPopulationGeneratedTile generatedTile = new ExponentialPopulationGeneratedTile();

        handwrittenTile.populationSizeInput.setTile(populationSizeTile);
        handwrittenTile.growthRateInput.setTile(growthRateTile);

        generatedTile.populationSizeInput.setTile(populationSizeTile);
        generatedTile.growthRateInput.setTile(growthRateTile);

        PopulationFunction handwrittenResult = handwrittenTile.applyTile(
                new BEASTState("handwritten-exponential-population"), new IdentityHashMap<>());

        PopulationFunction generatedResult =
                generatedTile.applyTile(new BEASTState("generated-exponential-population"), new IdentityHashMap<>());

        assertInstanceOf(ExponentialGrowth.class, handwrittenResult);

        assertInstanceOf(ExponentialGrowth.class, generatedResult);

        ExponentialGrowth handwrittenObject = (ExponentialGrowth) handwrittenResult;

        ExponentialGrowth generatedObject = (ExponentialGrowth) generatedResult;

        assertSame(handwrittenObject.popSizeParameterInput.get(), generatedObject.popSizeParameterInput.get());

        assertSame(handwrittenObject.growthRateParameterInput.get(), generatedObject.growthRateParameterInput.get());

        assertSame(populationSize, generatedObject.popSizeParameterInput.get());

        assertSame(growthRate, generatedObject.growthRateParameterInput.get());
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {

            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {

                return value;
            }
        };

        tile.setIndexVariables(Set.of());

        return tile;
    }
}
