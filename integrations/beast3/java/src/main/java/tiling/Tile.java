package tiling;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Field;
import java.util.*;

public abstract class Tile<T> {
    /* methods to find a tiling */

    /**
     * Returns the default priority of these tiles. Can be overridden by custom tiles.
     */
    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    /**
     * Returns the different stochasticity levels which the root of the AST subgraph covered by the tile can have.
     */
    protected Set<Stochasticity> getCompatibleStochasticities() {
        return EnumSet.allOf(Stochasticity.class);
    }

    /**
     * Returns the {@code TypeToken<?>} produced when by a successful application of this tile.
     * By default, this returns the type parameter {@code T} of {@code Tile<T>}. If this cannot be
     * determined at compile-time, a custom tile has to override this method.
     */
    public TypeToken<?> getTypeToken() {
        java.lang.reflect.Type rawType = TileUtils.getParametricType(this, 0);
        if (rawType == null) {
            throw new IllegalArgumentException("Tile " + this.getClass() + " has no return type parameter. Either specify the type in the type signature of the inheriting class, or override the getTypeToken method.");
        }
        return TypeToken.of(rawType);
    }

    /**
     * Tries to tile this tile to the AST subgraph starting with 'node'. Has to be overridden by custom tiles.
     */
    public abstract Set<Tile<?>> tryToTile(
            AstNode node, Map<AstNode,
            Set<Tile<?>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt;

    /**
     * Returns the {@code TileInput<?>} fields of this tile using reflection.
     */
    protected List<TileInput<?>> getTileInputs() {
        List<TileInput<?>> inputs = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (TileInput.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    TileInput<?> input = (TileInput<?>) field.get(this);
                    input.resolveTypeFromField(field);
                    inputs.add(input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return inputs;
    }

    /**
     * Creates wired up fresh tiles for the given inputs and their compatible input tiles.
     */
    protected Set<Tile<?>> getWiredUpTiles(
            List<TileInput<?>> tileInputs,
            List<Set<Tile<?>>> compatibleInputTiles
    ) {
        Set<Tile<?>> wiredUpTiles = new HashSet<>();

        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    Tile<?> wiredUpTile = this.createInstance();

                    // get TileInput fields from fresh instance

                    Map<String, TileInput<?>> freshInputsByKey = new HashMap<>();
                    for (TileInput<?> freshInput : wiredUpTile.getTileInputs()) {
                        freshInputsByKey.put(freshInput.getKey(), freshInput);
                    }

                    // wire each input tile and accumulate weight

                    int totalWeight = this.getPriority().getWeight();
                    for (int i = 0; i < tileInputs.size(); i++) {
                        Tile<?> inputTile = inputs.get(i);
                        String tileInputKey = tileInputs.get(i).getKey();

                        TileInput<?> freshInputTile = freshInputsByKey.get(tileInputKey);
                        freshInputTile.setTile(inputTile);

                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTiles.add(wiredUpTile);
                }
        );

        return wiredUpTiles;
    }

    /** methods to apply a tiling */

    private T result = null;

    /**
     * Applies the tile. Memoization is used to not apply the same tile twice.
     */
    public T apply(BEASTState beastState) {
        if (this.result == null) {
            this.result = this.applyTile(beastState);
        }
        return this.result;
    }

    /**
     * Applies the tile. This method should be overridden by custom tiles.
     */
    protected abstract T applyTile(BEASTState beastState);

    /** tiling weight */

    private int weight = 0;

    public int getWeight() {
        return this.weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /* helper */

    /**
     * Creates a new instance of this tile.
     */
    protected Tile<?> createInstance() {
        try {
            return this.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }
}
