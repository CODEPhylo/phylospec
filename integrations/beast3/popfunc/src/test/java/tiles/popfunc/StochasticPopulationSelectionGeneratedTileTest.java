package tiles.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;
import popfunc.beast.evolution.populationmodel.ConstantGrowth;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;

public class StochasticPopulationSelectionGeneratedTileTest {

    @Test
    public void exposesPackageInputContract() {
        StochasticPopulationSelectionGeneratedTile tile =
                new StochasticPopulationSelectionGeneratedTile();

        assertEquals("stochasticPopulationSelection", tile.getPhyloSpecGeneratorName());
        assertEquals("PopulationFunction", tile.getTypeToken().toString());
        assertEquals(
                List.of("indicator", "models"),
                tile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());
        assertEquals(
                "IntScalar<NonNegativeInt>",
                tile.getGeneratorTileInputs().get(0).getTypeToken().toString());
        assertEquals(
                "List<PopulationFunction>",
                tile.getGeneratorTileInputs().get(1).getTypeToken().toString());
    }

    @Test
    public void bindsIndicatorAndModels() {
        IntScalarParam<NonNegativeInt> indicator =
                new IntScalarParam<>(0, NonNegativeInt.INSTANCE);
        List<PopulationFunction> models = List.of(new ConstantGrowth(), new ConstantGrowth());
        StochasticPopulationSelectionGeneratedTile tile =
                new StochasticPopulationSelectionGeneratedTile();
        tile.indicatorInput.setTile(valueTile(indicator));
        tile.modelsInput.setTile(valueTile(models));

        PopulationFunction result =
                tile.applyTile(new BEASTState("popfunc-selection"), new IdentityHashMap<>());

        StochasticVariableSelection selection =
                assertInstanceOf(StochasticVariableSelection.class, result);
        assertSame(indicator, selection.indicatorInput.get());
        assertEquals(models, selection.modelsInput.get());
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
