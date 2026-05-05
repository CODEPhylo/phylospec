package org.phylospec.tiling.tiles;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.*;

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
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt;

    /**
     * Creates wired up fresh tiles for the given inputs and their compatible input tiles. For every combination of
     * compatible input tiles, a wired-up tile is created.
     */
    default Set<Tile<?, S>> getWiredUpTiles(
            List<TileInput<?, S>> tileInputs,
            List<Set<Tile<?, S>>> compatibleInputTiles,
            AstNode rootNode
    ) {
        Set<Tile<?, S>> wiredUpTiles = new HashSet<>();

        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    Tile<?, S> wiredUpTile = this.createInstance();

                    // get TileInput fields from fresh instance

                    Map<String, TileInput<?, S>> freshInputsByKey = new HashMap<>();
                    for (TileInput<?, S> freshInput : wiredUpTile.getTileInputs()) {
                        freshInputsByKey.put(freshInput.getKey(), freshInput);
                    }

                    // wire each input tile and accumulate weight

                    int totalWeight = this.getPriority().getWeight();
                    for (int i = 0; i < tileInputs.size(); i++) {
                        Tile<?, S> inputTile = inputs.get(i);
                        String tileInputKey = tileInputs.get(i).getKey();

                        TileInput<?, S> freshInputTile = freshInputsByKey.get(tileInputKey);
                        freshInputTile.setTile(inputTile);

                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTile.setRootNode(rootNode);

                    if (!wiredUpTile.isInconsistent(new IdentityHashMap<>())) {
                        wiredUpTiles.add(wiredUpTile);
                    }
                }
        );

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
            throw new RuntimeException(getClass().getSimpleName()  + " does not inherit from Tile<?>. In that case, implement createInstance yourself.");
        }

        try {
            return tile.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }

}
