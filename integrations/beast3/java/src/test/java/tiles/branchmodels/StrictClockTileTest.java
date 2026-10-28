package tiles.branchmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;

class StrictClockTileTest {

    @Test
    void omittedRateIsFixedOne() {
        Tree tree = new TreeParser("(A:1.0,B:1.0);");
        StrictClockTile tile = new StrictClockTile();
        tile.treeInput.setTile(valueTile(tree));

        BEASTState graph = new BEASTState("strict-clock-default");
        StrictClockModel clock = tile.applyTile(graph, new IdentityHashMap<>());
        graph.initializeBEASTObjects();

        assertEquals(1.0, clock.meanRateInput.get().get(), 0.0);
        assertSame(PositiveReal.INSTANCE, clock.meanRateInput.get().getDomain());
        assertEquals(1.0, clock.getRateForBranch(tree.getNode(0)), 0.0);

        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());

        clock.requiresRecalculation();
        assertEquals(1.0, clock.getRateForBranch(tree.getNode(0)), 0.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 2.5})
    void explicitRateKeepsItsConnection(double initialRate) {
        Tree tree = new TreeParser("(A:1.0,B:1.0);");
        RealScalarParam<PositiveReal> rate = new RealScalarParam<>(initialRate, PositiveReal.INSTANCE);

        StrictClockTile tile = new StrictClockTile();
        tile.treeInput.setTile(valueTile(tree));
        tile.rateInput.setTile(valueTile(rate));

        BEASTState graph = new BEASTState("strict-clock-explicit");
        StrictClockModel clock = tile.applyTile(graph, new IdentityHashMap<>());
        graph.initializeBEASTObjects();

        assertSame(rate, clock.meanRateInput.get());
        assertEquals(initialRate, clock.getRateForBranch(tree.getNode(0)), 0.0);

        rate.set(initialRate + 1.0);
        clock.requiresRecalculation();

        assertEquals(initialRate + 1.0, clock.getRateForBranch(tree.getNode(0)), 0.0);
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
