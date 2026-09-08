package pilot.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.evolution.tree.coalescent.PopulationFunction;
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
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.Tile;
import pilot.PilotTileLibrary;
import pilot.model.PilotPopulation;

public class PilotPopulationGeneratedTileTest {

    @Test
    public void registersGeneratedTileInPackageLibrary() {
        List<CandidateTile<BEASTState>> tiles =
                new PilotTileLibrary().getTiles();

        assertEquals(1, tiles.size());
        assertInstanceOf(
                PilotPopulationGeneratedTile.class,
                tiles.getFirst());
    }

    @Test
    public void discoversPackageLibraryThroughServiceLoader() {
        boolean discovered =
                TileLibrary.loadAll(BEASTState.class).stream()
                        .anyMatch(
                                PilotPopulationGeneratedTile.class
                                        ::isInstance);

        assertTrue(discovered);
    }

    @Test
    public void appliesGeneratedTileToAnnotatedInput() {
        RealScalarParam<PositiveReal> populationSize =
                new RealScalarParam<>(
                        250.0,
                        PositiveReal.INSTANCE);

        Tile<RealScalar<? extends PositiveReal>, BEASTState> valueTile =
                valueTile(populationSize);

        PilotPopulationGeneratedTile generatedTile =
                new PilotPopulationGeneratedTile();

        generatedTile.populationSizeInput.setTile(valueTile);

        PopulationFunction result =
                generatedTile.applyTile(
                        new BEASTState("package-pilot"),
                        new IdentityHashMap<>());

        PilotPopulation population =
                assertInstanceOf(
                        PilotPopulation.class,
                        result);

        assertSame(
                populationSize,
                population.populationSize.get());

        assertEquals(
                250.0,
                population.getPopSize(10.0));
    }

    private static Tile<RealScalar<? extends PositiveReal>, BEASTState> valueTile(
            RealScalarParam<PositiveReal> value) {

        Tile<RealScalar<? extends PositiveReal>, BEASTState> tile =
                new Tile<>() {

                    @Override
                    protected RealScalar<? extends PositiveReal> applyTile(
                            BEASTState state,
                            IdentityHashMap<Expr.Variable, Integer> indexVariables) {

                        return value;
                    }
                };

        tile.setIndexVariables(Set.of());
        return tile;
    }
}
