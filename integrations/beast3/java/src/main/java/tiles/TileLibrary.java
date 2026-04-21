package tiles;

import tiling.Tile;

import java.util.List;
import java.util.ServiceLoader;
import java.util.ArrayList;

public abstract class TileLibrary {

    /** Returns all tiles registered in this library. */
    public abstract List<Tile<?>> getTiles();

    /** Discovers all TileLibrary implementations on the classpath and collects their tiles. */
    public static List<Tile<?>> loadAll() {
        List<Tile<?>> all = new ArrayList<>();
        for (TileLibrary library : ServiceLoader.load(TileLibrary.class)) {
            all.addAll(library.getTiles());
        }
        return all;
    }

}
