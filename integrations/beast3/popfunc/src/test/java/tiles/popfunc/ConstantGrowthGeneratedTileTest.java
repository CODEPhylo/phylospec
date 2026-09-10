package tiles.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.Tile;
import popfunc.beast.evolution.populationmodel.ConstantGrowth;

public class ConstantGrowthGeneratedTileTest {

    @Test
    public void exposesCanonicalInputContract() {
        ConstantGrowthGeneratedTile tile = new ConstantGrowthGeneratedTile();

        assertEquals("constantPopulationFunction", tile.getPhyloSpecGeneratorName());
        assertEquals("PopulationFunction", tile.getTypeToken().toString());
        assertEquals(
                List.of("populationSize"),
                tile.getGeneratorTileInputs().stream()
                        .map(input -> input.getPhylospecArgumentName())
                        .toList());
        assertEquals(
                "RealScalar<PositiveReal>",
                tile.getGeneratorTileInputs().getFirst().getTypeToken().toString());
    }

    @Test
    public void bindsPopulationSizeToPopFuncInput() {
        RealScalarParam<PositiveReal> populationSize =
                new RealScalarParam<>(500.0, PositiveReal.INSTANCE);
        ConstantGrowthGeneratedTile tile = new ConstantGrowthGeneratedTile();
        tile.populationSizeInput.setTile(valueTile(populationSize));

        PopulationFunction result =
                tile.applyTile(new BEASTState("popfunc-constant"), new IdentityHashMap<>());

        ConstantGrowth constant = assertInstanceOf(ConstantGrowth.class, result);
        assertSame(populationSize, constant.popSizeParameterInput.get());
        assertEquals(500.0, constant.getN0());
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
