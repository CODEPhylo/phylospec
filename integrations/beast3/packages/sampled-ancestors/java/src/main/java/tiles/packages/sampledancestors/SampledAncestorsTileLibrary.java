package tiles.packages.sampledancestors;

import beastconfig.BEASTState;
import java.util.List;
import org.phylospec.tiling.tiles.CandidateTile;
import tiles.BeastPackageTileLibrary;

/** Tiles backed by the BEAST sampled-ancestors package. */
public class SampledAncestorsTileLibrary extends BeastPackageTileLibrary {

    private static final String FBD_MODEL_CLASS =
            "sa.evolution.speciation.SABirthDeathModel";

    @Override
    public String getPackageId() {
        return "io.github.compevol:sampled-ancestors";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(FBD_MODEL_CLASS, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    @Override
    public List<CandidateTile<BEASTState>> getTiles() {
        return List.of(new FossilizedBirthDeathTile());
    }
}
