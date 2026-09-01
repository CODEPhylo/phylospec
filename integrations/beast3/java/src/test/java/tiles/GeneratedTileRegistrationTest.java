package tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.substitutionmodels.JC69GeneratedTile;

public class GeneratedTileRegistrationTest {

    @Test
    public void registersGeneratedJC69ExactlyOnce() {
        List<CandidateTile<BEASTState>> jc69Tiles = new BeastCoreTileLibrary()
                .getTiles().stream().filter(this::isJC69Tile).toList();

        assertEquals(1, jc69Tiles.size());

        assertInstanceOf(JC69GeneratedTile.class, jc69Tiles.getFirst());
    }

    @Test
    public void discoversGeneratedJC69ThroughServiceLoader() {
        List<CandidateTile<BEASTState>> jc69Tiles = TileLibrary.loadAll(BEASTState.class).stream()
                .filter(this::isJC69Tile)
                .toList();

        assertEquals(1, jc69Tiles.size());

        assertInstanceOf(JC69GeneratedTile.class, jc69Tiles.getFirst());
    }

    private boolean isJC69Tile(CandidateTile<BEASTState> candidateTile) {

        return candidateTile instanceof GeneratorTile<?, ?> generatorTile
                && "jc69".equals(generatorTile.getPhyloSpecGeneratorName());
    }
}
