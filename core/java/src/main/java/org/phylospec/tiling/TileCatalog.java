package org.phylospec.tiling;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.tiling.tiles.CandidateTile;

/**
 * A consistent view of the tiles and metadata supplied by one ordered set of tile libraries.
 */
public final class TileCatalog<S> {

    private final List<TileLibrary<S>> libraries;
    private final List<CandidateTile<S>> tiles;
    private final List<ComponentLibrary> componentLibraries;
    private final ComponentResolver componentResolver;
    private final List<EngineSpecificationSchema> engineSpecifications;

    private TileCatalog(List<TileLibrary<S>> libraries, boolean applyProviderPrecedence) throws IOException {
        this.libraries = List.copyOf(libraries);
        this.tiles = applyProviderPrecedence
                ? TileLibrary.combine(this.libraries)
                : TileLibrary.collectTiles(this.libraries);
        this.componentLibraries = List.copyOf(TileLibrary.loadComponentLibraries(this.libraries));
        this.componentResolver = new ComponentResolver(this.componentLibraries);
        this.engineSpecifications = TileLibrary.loadEngineSpecifications(this.libraries);
    }

    /** Discovers every compatible library without choosing between duplicate providers. */
    public static <S> TileCatalog<S> discover(Class<S> stateType) throws IOException {
        return new TileCatalog<>(TileLibrary.discover(stateType), false);
    }

    /** Selects libraries in preference order, with an earlier provider overriding a later one. */
    public static <S> TileCatalog<S> select(Class<S> stateType, List<String> libraryIds) throws IOException {
        Objects.requireNonNull(libraryIds, "libraryIds");
        return new TileCatalog<>(TileLibrary.select(stateType, libraryIds), true);
    }

    public List<TileLibrary<S>> getLibraries() {
        return libraries;
    }

    public List<CandidateTile<S>> getTiles() {
        return tiles;
    }

    public List<ComponentLibrary> getComponentLibraries() {
        return componentLibraries;
    }

    public ComponentResolver getComponentResolver() {
        return componentResolver;
    }

    public List<EngineSpecificationSchema> getEngineSpecifications() {
        return engineSpecifications;
    }

    /** Applies package-specific post-tiling configuration using this catalog's libraries. */
    public void configureState(S state) {
        TileLibrary.configureState(libraries, state);
    }
}
