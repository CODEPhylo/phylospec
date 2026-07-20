package org.phylospec.tiling;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.phylospec.tiling.tiles.CandidateTile;

public abstract class TileLibrary<S> {

    /** Returns all tiles registered in this library. */
    public abstract List<CandidateTile<S>> getTiles();

    /** Discovers all TileLibrary implementations on the classpath and collects their tiles. */
    public static <S> List<CandidateTile<S>> loadAll() {
        List<CandidateTile<S>> all = new ArrayList<>();
        for (TileLibrary<S> library : ServiceLoader.load(TileLibrary.class)) {
            all.addAll(library.getTiles());
        }
        return all;
    }
}
