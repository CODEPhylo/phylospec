package org.phylospec.tiling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;

public abstract class TileLibrary<S> {

    /**
     * Stable identifier used when an application explicitly selects engine or package libraries.
     * Package adapters should override this with their package identifier.
     */
    public String getId() {
        return getClass().getName();
    }

    /** Returns the state type this library's tiles apply to. */
    public abstract Class<S> getStateType();

    /** Returns all tiles registered in this library. */
    public abstract List<CandidateTile<S>> getTiles();

    /** Discovers the Tile libraries whose state type is compatible with {@code stateType}. */
    public static <S> List<TileLibrary<S>> discover(Class<S> stateType) {
        Objects.requireNonNull(stateType, "stateType");

        List<TileLibrary<S>> libraries = new ArrayList<>();
        for (TileLibrary<?> library : ServiceLoader.load(TileLibrary.class)) {
            if (library.getStateType().isAssignableFrom(stateType)) {
                @SuppressWarnings("unchecked")
                TileLibrary<S> typed = (TileLibrary<S>) library;
                libraries.add(typed);
            }
        }
        return libraries;
    }

    /**
     * Discovers all TileLibrary implementations on the classpath and collects the tiles of
     * those that apply to {@code stateType}, that is, those whose state type is {@code stateType}
     * itself or a supertype of it.
     */
    public static <S> List<CandidateTile<S>> loadAll(Class<S> stateType) {
        List<CandidateTile<S>> all = new ArrayList<>();
        for (TileLibrary<S> library : discover(stateType)) {
            all.addAll(library.getTiles());
        }
        return all;
    }

    /**
     * Loads only the requested libraries. Identifiers are ordered from most to least preferred;
     * when two different libraries provide the same generator mapping, the earlier library wins.
     * Other mappings and non-generator Tiles from both libraries remain available.
     */
    public static <S> List<CandidateTile<S>> loadSelected(Class<S> stateType, List<String> libraryIds) {
        Objects.requireNonNull(libraryIds, "libraryIds");

        Map<String, TileLibrary<S>> discoveredById = new LinkedHashMap<>();
        for (TileLibrary<S> library : discover(stateType)) {
            String id = requireId(library);
            TileLibrary<S> duplicate = discoveredById.putIfAbsent(id, library);
            if (duplicate != null) {
                throw new IllegalStateException("Multiple Tile libraries use the id '"
                        + id
                        + "': "
                        + duplicate.getClass().getName()
                        + " and "
                        + library.getClass().getName()
                        + ".");
            }
        }

        List<TileLibrary<S>> selected = new ArrayList<>();
        Set<String> requestedIds = new HashSet<>();
        for (String id : libraryIds) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Tile library identifiers must not be blank.");
            }
            if (!requestedIds.add(id)) {
                throw new IllegalArgumentException("Tile library '" + id + "' was selected more than once.");
            }

            TileLibrary<S> library = discoveredById.get(id);
            if (library == null) {
                throw new IllegalArgumentException("Tile library '"
                        + id
                        + "' is not installed. Available libraries are "
                        + discoveredById.keySet()
                        + ".");
            }
            selected.add(library);
        }

        return combine(selected);
    }

    /**
     * Combines libraries in preference order. Equivalent generator mappings from a later library
     * are omitted, while duplicate mappings within one library are retained so the evaluator can
     * report the invalid ambiguity.
     */
    public static <S> List<CandidateTile<S>> combine(List<? extends TileLibrary<S>> libraries) {
        Objects.requireNonNull(libraries, "libraries");

        List<CandidateTile<S>> combined = new ArrayList<>();
        Set<GeneratorTileMappingDescriptor.Signature> higherPriorityMappings = new HashSet<>();

        for (TileLibrary<S> library : libraries) {
            Objects.requireNonNull(library, "libraries must not contain null");
            Set<GeneratorTileMappingDescriptor.Signature> currentMappings = new HashSet<>();

            for (CandidateTile<S> tile : library.getTiles()) {
                if (!(tile instanceof GeneratorTile<?, ?> generatorTile)) {
                    combined.add(tile);
                    continue;
                }

                GeneratorTileMappingDescriptor.Signature signature =
                        generatorTile.getMappingDescriptor().signature();
                if (!higherPriorityMappings.contains(signature)) {
                    combined.add(tile);
                    currentMappings.add(signature);
                }
            }

            higherPriorityMappings.addAll(currentMappings);
        }

        return combined;
    }

    private static String requireId(TileLibrary<?> library) {
        String id = Objects.requireNonNull(library.getId(), "Tile library id");
        if (id.isBlank()) {
            throw new IllegalStateException("Tile library " + library.getClass().getName() + " has a blank id.");
        }
        return id;
    }
}
