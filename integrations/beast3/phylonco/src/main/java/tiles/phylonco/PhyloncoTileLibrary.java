package tiles.phylonco;

import beastconfig.BEASTState;
import java.util.List;
import org.phylospec.annotations.ComponentSource;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;

/** BEAST 3 tiles for beast-phylonco: mpileup read counts, GT10 and the read-count tree likelihood. */
@ComponentSource("/phylonco-components.json")
public final class PhyloncoTileLibrary extends TileLibrary<BEASTState> {

    @Override
    public String getId() {
        return "phylonco";
    }

    @Override
    public Class<BEASTState> getStateType() {
        return BEASTState.class;
    }

    @Override
    public List<CandidateTile<BEASTState>> getTiles() {
        return List.of(
                new FromMpileupTile(),
                new ReadCountAccessorTiles.Taxa(),
                new ReadCountAccessorTiles.NumTaxa(),
                new ReadCountAccessorTiles.NumSites(),
                new GT10Tile(),
                new ReadCountModelTile(),
                new ObservedAsReadCountTile());
    }

    @Override
    public List<String> getEngineSpecificationResources() {
        return List.of("/META-INF/phylospec/engines/phylonco.json");
    }
}
