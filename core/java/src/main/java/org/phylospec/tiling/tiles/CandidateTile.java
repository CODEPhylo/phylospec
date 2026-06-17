package org.phylospec.tiling.tiles;

import java.util.*;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.DimensionUnifier;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.DimensionResolver;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/**
 * This interface provides methods to construct tiles for a subgraph of the PhyloSpec AST.
 */
public interface CandidateTile<S> {

    /**
     * Tries to tile this tile to the AST subgraph starting with 'node'. Has to be overridden by custom candidate tiles.
     * Returns a set of the possible tilings.
     */
    Set<Tile<?, S>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, S>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt;

    /**
     * Backwards-compatible entry point for callers that do not use dimension
     * resolution. New tiling code should pass a DimensionResolver.
     * @deprecated
     */
    default Set<Tile<?, S>> getWiredUpTiles(
            List<TileInput<?, S>> tileInputs,
            List<Set<Tile<?, S>>> compatibleInputTiles,
            AstNode rootNode)
            throws FailedTilingAttempt.RejectedBoundary {
        return getWiredUpTiles(tileInputs, compatibleInputTiles, rootNode, null);
    }

    /**
     * Creates wired up fresh tiles for the given inputs and their compatible input tiles.
     *
     * Dimension requirements are checked again at the combination level using
     * one shared DimensionUnifier. This allows multiple inputs of the same tile
     * to share a dimension variable, e.g. D.
     */
    default Set<Tile<?, S>> getWiredUpTiles(
            List<TileInput<?, S>> tileInputs,
            List<Set<Tile<?, S>>> compatibleInputTiles,
            AstNode rootNode,
            DimensionResolver dimensionResolver)
            throws FailedTilingAttempt.RejectedBoundary {
        Set<Tile<?, S>> wiredUpTiles = new HashSet<>();
        boolean[] rejectedByDimensionUnification = new boolean[] {false};

        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    Tile<?, S> wiredUpTile = this.createInstance();

                    Map<String, TileInput<?, S>> freshInputsByKey = new HashMap<>();
                    for (TileInput<?, S> freshInput : wiredUpTile.getTileInputs()) {
                        freshInputsByKey.put(freshInput.getKey(), freshInput);
                    }

                    int totalWeight = this.getPriority().getWeight();

                    DimensionUnifier dimensionUnifier = new DimensionUnifier();

                    boolean dimensionsCompatible = true;

                    for (int i = 0; i < tileInputs.size(); i++) {
                        Tile<?, S> inputTile = inputs.get(i);
                        String tileInputKey = tileInputs.get(i).getKey();

                        TileInput<?, S> freshInputTile = freshInputsByKey.get(tileInputKey);
                        freshInputTile.setTile(inputTile);

                        DimensionUnifier.Result dimensionResult =
                                freshInputTile.unifyRequiredDimension(
                                        inputTile,
                                        inputTile.getRootNode(),
                                        dimensionResolver,
                                        dimensionUnifier);

                        if (dimensionResult != DimensionUnifier.Result.MATCH) {
                            dimensionsCompatible = false;
                            rejectedByDimensionUnification[0] = true;
                            break;
                        }

                        totalWeight += inputTile.getWeight();
                    }

                    if (!dimensionsCompatible) {
                        return;
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTile.setRootNode(rootNode);

                    if (!wiredUpTile.isInconsistent(new IdentityHashMap<>())) {
                        wiredUpTiles.add(wiredUpTile);
                    }
                });

        if (wiredUpTiles.isEmpty() && rejectedByDimensionUnification[0]) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    "The target backend cannot use these inputs together because their dimensions do not unify.");
        }

        return wiredUpTiles;
    }

    /**
     * Returns the different stochasticity levels which the root of the AST subgraph covered by the tile can have.
     */
    default Set<Stochasticity> getCompatibleStochasticities() {
        return EnumSet.allOf(Stochasticity.class);
    }

    /**
     * Returns the default priority of these tiles. Can be overridden by custom tiles.
     */
    default TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    /**
     * Creates a new instance of the corresponding tile.
     * The default method assumes that the tile itself implements {@code CandidateTile}. If this is not the case,
     * the custom candidate tile has to implement this.
     */
    default Tile<?, S> createInstance() {
        if (!(this instanceof Tile<?, ?> tile)) {
            throw new RuntimeException(
                    getClass().getSimpleName()
                            + " does not inherit from Tile<?>. In that case, implement createInstance yourself.");
        }

        try {
            return tile.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }
}
