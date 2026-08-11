package tiles;

import beastconfig.BEASTState;
import org.phylospec.tiling.TileLibrary;

/**
 * Base class for tile libraries backed by an optional BEAST package.
 *
 * <p>Package integrations are discovered through {@link java.util.ServiceLoader}. An
 * integration should only report itself as available when its BEAST package can be used in
 * the current runtime.
 */
public abstract class BeastPackageTileLibrary extends TileLibrary<BEASTState> {

    /** Returns the stable identifier of the BEAST package that provides these tiles. */
    public abstract String getPackageId();

    /** Returns whether the package is available in the current runtime. */
    public abstract boolean isAvailable();
}
