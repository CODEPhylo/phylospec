package tiles.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;
import popfunc.beast.evolution.populationmodel.ExpansionGrowth;

public class ExpansionGrowthGeneratedTileTest {

    @Test
    public void bindsAncestralPopulationWhenPresent() {
        RealScalarParam<PositiveReal> populationSize = positive(1_000.0);
        RealScalarParam<PositiveReal> growthRate = positive(0.25);
        RealScalarParam<NonNegativeReal> transitionAge = age(5.0);
        RealScalarParam<PositiveReal> ancestralSize = positive(100.0);

        ExpansionGrowthGeneratedTile tile = tile(populationSize, growthRate, transitionAge);
        tile.ancestralPopulationSizeInput.setTile(valueTile(ancestralSize));

        BEASTState state = new BEASTState("popfunc-expansion");
        PopulationFunction result = tile.applyTile(state, new IdentityHashMap<>());
        ExpansionGrowth expansion = assertInstanceOf(ExpansionGrowth.class, result);
        state.initBEASTObject(expansion);

        assertSame(populationSize, expansion.NCInput.get());
        assertSame(growthRate, expansion.rInput.get());
        assertSame(transitionAge, expansion.xInput.get());
        assertSame(ancestralSize, expansion.NAInput.get());
        assertEquals(1, expansion.I_naInput.get().get());
    }

    @Test
    public void suppliesSafeFallbacksWhenAncestralPopulationIsMissing() {
        ExpansionGrowthGeneratedTile tile =
                tile(positive(1_000.0), positive(0.25), age(5.0));

        BEASTState state = new BEASTState("popfunc-expansion-default");
        PopulationFunction result = tile.applyTile(state, new IdentityHashMap<>());
        ExpansionGrowth expansion = assertInstanceOf(ExpansionGrowth.class, result);
        state.initBEASTObject(expansion);

        assertEquals(1.0, expansion.NAInput.get().get());
        assertEquals(0, expansion.I_naInput.get().get());
        assertEquals(1_000.0, expansion.getPopSize(2.0));
    }

    private static ExpansionGrowthGeneratedTile tile(
            RealScalarParam<PositiveReal> populationSize,
            RealScalarParam<PositiveReal> growthRate,
            RealScalarParam<NonNegativeReal> transitionAge) {

        ExpansionGrowthGeneratedTile tile = new ExpansionGrowthGeneratedTile();
        tile.populationSizeInput.setTile(valueTile(populationSize));
        tile.growthRateInput.setTile(valueTile(growthRate));
        tile.transitionAgeInput.setTile(valueTile(transitionAge));
        return tile;
    }

    private static RealScalarParam<PositiveReal> positive(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }

    private static RealScalarParam<NonNegativeReal> age(double value) {
        return new RealScalarParam<>(value, NonNegativeReal.INSTANCE);
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
