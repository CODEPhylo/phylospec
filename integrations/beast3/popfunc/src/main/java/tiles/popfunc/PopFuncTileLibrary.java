package tiles.popfunc;

import beastconfig.BEASTState;
import java.util.List;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import tiles.popfunc.generated.GeneratedTileRegistry;

public final class PopFuncTileLibrary extends TileLibrary<BEASTState> {

    @Override
    public Class<BEASTState> getStateType() {
        return BEASTState.class;
    }

    @Override
    public List<CandidateTile<BEASTState>> getTiles() {
        return GeneratedTileRegistry.createTiles();
    }
}
