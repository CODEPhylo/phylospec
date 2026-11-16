package org.phylospec.tiling.tiles;

import java.util.*;
import java.util.function.Supplier;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.PrunedTilingException;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeUtils;
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
     * The maximum number of wired-up tiles kept per candidate tile, AST node and type. If unbound, all are kept.
     * Use {@link #runWithMaxWiredUpTiles} and {@link #getMaxWiredUpTiles} instead of accessing it directly.
     */
    ScopedValue<Integer> MAX_WIRED_UP_TILES = ScopedValue.newInstance();

    /**
     * The number of lightest wired-up tiles kept per candidate tile, AST node and type within
     * {@link #runWithMaxWiredUpTiles}.
     */
    int LIGHTEST_WIRED_UP_TILES = 16;

    /**
     * Runs the given tiling while {@link #getWiredUpTiles} keeps at most {@link #LIGHTEST_WIRED_UP_TILES} tiles per
     * candidate tile, AST node and type. The limit is passed via a {@link ScopedValue}, as {@link #getWiredUpTiles} is
     * called from within the {@link #tryToTile} implementations, which have no parameter for it. It only applies
     * while the tiling runs, so it never leaks into other tilings, even if the tiling throws.
     */
    static <T> T runWithMaxWiredUpTiles(Supplier<T> tiling) {
        return ScopedValue.where(MAX_WIRED_UP_TILES, LIGHTEST_WIRED_UP_TILES).call(tiling::get);
    }

    /**
     * Creates wired up fresh tiles for the given inputs and their compatible input tiles. For every type the
     * wired-up tiles can have, the tiles of the {@link #getMaxWiredUpTiles} lightest consistent combinations of
     * compatible input tiles are returned. The limit applies per type, as the tiles using the returned tiles as
     * inputs pick them by type, so no type they could pick is lost.
     *
     * <p>The tiles which are not returned due to the limit are pruned. They are never lighter than the returned
     * tiles of their type, so they are only needed if the returned ones are inconsistent with other tiles.
     *
     * <p>Tiles can also be missing because a combination is only consistent with the pruned alternatives of its
     * input tiles. The lighter returned tiles of their type are then marked as having pruned alternatives as well.
     *
     * @throws PrunedTilingException if an inconsistent combination could be consistent with the pruned alternatives
     *     of its input tiles, and no lighter tile of its type exists
     */
    default Set<Tile<?, S>> getWiredUpTiles(
            List<TileInput<?, S>> tileInputs, List<Set<Tile<?, S>>> compatibleInputTiles, AstNode rootNode) {
        Set<Tile<?, S>> wiredUpTiles = new HashSet<>();
        Map<TypeToken<?>, List<Tile<?, S>>> wiredUpTilesPerType = new HashMap<>();

        // heavier combinations can only lead to tiles of another type if the type depends on the input tiles
        boolean hasFixedTypeToken = this.createInstance().hasFixedTypeToken();

        // ties are broken by identity, so that all consumers of a shared input tile prefer the same one
        Comparator<Tile<?, S>> byWeight =
                Comparator.<Tile<?, S>>comparingInt(Tile::getWeight).thenComparingInt(System::identityHashCode);
        List<List<Tile<?, S>>> sortedInputTiles = compatibleInputTiles.stream()
                .map(tiles -> tiles.stream().sorted(byWeight).toList())
                .toList();

        Utils.visitCombinationsByWeight(sortedInputTiles, Tile::getWeight, inputs -> {
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

            TypeToken<?> typeToken = wiredUpTile.getTypeToken();

            if (wiredUpTile.isInconsistent(new IdentityHashMap<>())) {
                // a pruned alternative of an input tile could be consistent with the other input tiles. this would
                // lead to a tile of this type which is missing in the returned tiles

                if (inputs.stream().anyMatch(Tile::hasPrunedAlternatives)) {
                    List<Tile<?, S>> lighterTilesOfType = wiredUpTilesPerType.get(typeToken);

                    // without a lighter tile of this type, the missing tile could be the lightest one
                    if (lighterTilesOfType == null) throw new PrunedTilingException();

                    // the missing tile is only needed if the lighter ones are inconsistent with other tiles
                    lighterTilesOfType.forEach(Tile::markPrunedAlternatives);
                }

                return TypeUtils.VisitorResult.CONTINUE;
            }

            // the combinations are visited lightest first, so the lightest tiles of every type are kept

            List<Tile<?, S>> wiredUpTilesOfType =
                    wiredUpTilesPerType.computeIfAbsent(typeToken, ignored -> new ArrayList<>());

            if (wiredUpTilesOfType.size() < getMaxWiredUpTiles()) {
                wiredUpTilesOfType.add(wiredUpTile);
                wiredUpTiles.add(wiredUpTile);
                return TypeUtils.VisitorResult.CONTINUE;
            }

            // this tile is pruned

            wiredUpTilesOfType.forEach(Tile::markPrunedAlternatives);
            return hasFixedTypeToken ? TypeUtils.VisitorResult.STOP : TypeUtils.VisitorResult.CONTINUE;
        });

        return wiredUpTiles;
    }

    /**
     * Returns the maximum number of wired-up tiles kept per candidate tile, AST node and type. This is unlimited
     * outside of {@link #runWithMaxWiredUpTiles}.
     */
    static int getMaxWiredUpTiles() {
        return MAX_WIRED_UP_TILES.orElse(Integer.MAX_VALUE);
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
            throw new RuntimeException(getClass().getSimpleName()
                    + " does not inherit from Tile<?>. In that case, implement createInstance yourself.");
        }

        try {
            return tile.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }
}
