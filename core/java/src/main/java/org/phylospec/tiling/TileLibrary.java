package org.phylospec.tiling;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.phylospec.tiling.tiles.CandidateTile;

public abstract class TileLibrary<S> {

    /** Returns the state type this library's tiles apply to. */
    public abstract Class<S> getStateType();

    /** Returns all tiles registered in this library. */
    public abstract List<CandidateTile<S>> getTiles();

    /**
     * Discovers all TileLibrary implementations on the classpath and collects the tiles of
     * those whose state type matches {@code stateType}.
     */
    public static <S> List<CandidateTile<S>> loadAll(Class<S> stateType) {
        List<CandidateTile<S>> all = new ArrayList<>();
        for (TileLibrary<?> library : ServiceLoader.load(TileLibrary.class)) {
            if (library.getStateType().isAssignableFrom(stateType)) {
                @SuppressWarnings("unchecked")
                TileLibrary<S> typed = (TileLibrary<S>) library;
                all.addAll(typed.getTiles());
            }
        }
        return all;
    }
}
