package tiles.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;
import popfunc.beast.evolution.populationmodel.ExponentialGrowth;

public class ExponentialGrowthGeneratedTileTest {

    @Test
    public void bindsAncestralSizeAndIndicator() {
        RealScalarParam<PositiveReal> populationSize =
                new RealScalarParam<>(1_000.0, PositiveReal.INSTANCE);
        RealScalarParam<Real> growthRate =
                new RealScalarParam<>(0.25, Real.INSTANCE);
        RealScalarParam<PositiveReal> ancestralPopulationSize =
                new RealScalarParam<>(100.0, PositiveReal.INSTANCE);

        ExponentialGrowthGeneratedTile tile =
                new ExponentialGrowthGeneratedTile();
        tile.populationSizeInput.setTile(valueTile(populationSize));
        tile.growthRateInput.setTile(valueTile(growthRate));
        tile.ancestralPopulationSizeInput.setTile(valueTile(ancestralPopulationSize));

        PopulationFunction result =
                tile.applyTile(
                        new BEASTState("popfunc-exponential"),
                        new IdentityHashMap<>());

        ExponentialGrowth exponential =
                assertInstanceOf(ExponentialGrowth.class, result);
        assertSame(populationSize, exponential.popSizeParameterInput.get());
        assertSame(growthRate, exponential.growthRateParameterInput.get());
        assertSame(
                ancestralPopulationSize,
                exponential.ancestralPopulationParameterInput.get());
        assertEquals(100.0, exponential.getNA());
    }

    @Test
    public void disablesAncestralSizeWhenItIsMissing() {
        RealScalarParam<PositiveReal> populationSize =
                new RealScalarParam<>(1_000.0, PositiveReal.INSTANCE);
        RealScalarParam<Real> growthRate =
                new RealScalarParam<>(0.25, Real.INSTANCE);

        ExponentialGrowthGeneratedTile tile =
                new ExponentialGrowthGeneratedTile();
        tile.populationSizeInput.setTile(valueTile(populationSize));
        tile.growthRateInput.setTile(valueTile(growthRate));

        PopulationFunction result =
                tile.applyTile(
                        new BEASTState("popfunc-exponential-default"),
                        new IdentityHashMap<>());

        ExponentialGrowth exponential =
                assertInstanceOf(ExponentialGrowth.class, result);
        assertSame(populationSize, exponential.popSizeParameterInput.get());
        assertSame(growthRate, exponential.growthRateParameterInput.get());
        assertEquals(0, exponential.indicatorParameterInput.get().get());
        assertEquals(0.0, exponential.getNA());
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {
        Tile<T, BEASTState> tile = new Tile<>() {
            @Override
            protected T applyTile(
                    BEASTState state,
                    IdentityHashMap<Expr.Variable, Integer> indexVariables) {
                return value;
            }
        };

        tile.setIndexVariables(Set.of());
        return tile;
    }
}
