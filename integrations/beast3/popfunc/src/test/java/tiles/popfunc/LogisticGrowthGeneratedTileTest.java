package tiles.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.Tile;
import popfunc.beast.evolution.populationmodel.LogisticGrowth;

public class LogisticGrowthGeneratedTileTest {

    @Test
    public void registersTheGeneratedPopFuncTiles() {
        List<CandidateTile<BEASTState>> tiles = new PopFuncTileLibrary().getTiles();

        assertEquals(2, tiles.size());
        assertEquals(1, tiles.stream().filter(LogisticGrowthGeneratedTile.class::isInstance).count());
        assertEquals(1, tiles.stream().filter(ConstantGrowthGeneratedTile.class::isInstance).count());
    }

    @Test
    public void discoversTheTileThroughServiceLoader() {
        boolean discovered = TileLibrary.loadAll(BEASTState.class).stream()
                .anyMatch(LogisticGrowthGeneratedTile.class::isInstance);

        assertTrue(discovered);
    }

    @Test
    public void bindsArgumentsToThePopFuncInputs() {
        RealScalarParam<NonNegativeReal> inflectionAge =
                new RealScalarParam<>(5.0, NonNegativeReal.INSTANCE);
        RealScalarParam<PositiveReal> carryingCapacity =
                new RealScalarParam<>(1_000.0, PositiveReal.INSTANCE);
        RealScalarParam<NonNegativeReal> growthRate =
                new RealScalarParam<>(0.25, NonNegativeReal.INSTANCE);
        RealScalarParam<NonNegativeReal> ancestralPopulationSize =
                new RealScalarParam<>(100.0, NonNegativeReal.INSTANCE);

        LogisticGrowthGeneratedTile tile = new LogisticGrowthGeneratedTile();
        tile.inflectionAgeInput.setTile(valueTile(inflectionAge));
        tile.carryingCapacityInput.setTile(valueTile(carryingCapacity));
        tile.growthRateInput.setTile(valueTile(growthRate));
        tile.ancestralPopulationSizeInput.setTile(valueTile(ancestralPopulationSize));

        PopulationFunction result =
                tile.applyTile(new BEASTState("popfunc-logistic"), new IdentityHashMap<>());

        LogisticGrowth logistic = assertInstanceOf(LogisticGrowth.class, result);
        assertSame(inflectionAge, logistic.t50Input.get());
        assertSame(carryingCapacity, logistic.nCarryingCapacityInput.get());
        assertSame(growthRate, logistic.bInput.get());
        assertSame(ancestralPopulationSize, logistic.NAInput.get());
        assertEquals(5.0, logistic.getT50());
        assertEquals(1_000.0, logistic.getNCarryingCapacity());
        assertEquals(0.25, logistic.getGrowthRateB());
        assertEquals(100.0, logistic.getRawNA());
        assertEquals(100.0, logistic.getEffectiveNA());
    }

    @Test
    public void leavesOptionalAncestralSizeUnset() {
        RealScalarParam<NonNegativeReal> inflectionAge =
                new RealScalarParam<>(5.0, NonNegativeReal.INSTANCE);
        RealScalarParam<PositiveReal> carryingCapacity =
                new RealScalarParam<>(1_000.0, PositiveReal.INSTANCE);
        RealScalarParam<NonNegativeReal> growthRate =
                new RealScalarParam<>(0.25, NonNegativeReal.INSTANCE);

        LogisticGrowthGeneratedTile tile = new LogisticGrowthGeneratedTile();
        tile.inflectionAgeInput.setTile(valueTile(inflectionAge));
        tile.carryingCapacityInput.setTile(valueTile(carryingCapacity));
        tile.growthRateInput.setTile(valueTile(growthRate));

        PopulationFunction result =
                tile.applyTile(new BEASTState("popfunc-logistic-no-ancestral"), new IdentityHashMap<>());

        LogisticGrowth logistic = assertInstanceOf(LogisticGrowth.class, result);
        assertNull(logistic.NAInput.get());
        assertEquals(0.0, logistic.getRawNA());
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
