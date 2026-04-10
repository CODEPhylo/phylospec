package tiling;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.Map;
import java.util.Set;

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
        return Set.of(
                Stochasticity.CONSTANT,
                Stochasticity.DETERMINISTIC,
                Stochasticity.STOCHASTIC,
                Stochasticity.UNOBSERVED_STOCHASTIC,
                Stochasticity.UNDEFINED
        );
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
    );

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
