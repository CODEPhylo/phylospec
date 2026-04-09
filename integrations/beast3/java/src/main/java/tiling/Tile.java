package tiling;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.Map;
import java.util.Set;

public abstract class Tile<T> {
    public abstract Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver);

    private T result = null;
    public T apply(BEASTState beastState) {
        if (this.result == null) {
            this.result = this.applyTile(beastState);
        }
        return this.result;
    }

    protected abstract T applyTile(BEASTState beastState);

    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    protected Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(
                Stochasticity.CONSTANT,
                Stochasticity.DETERMINISTIC,
                Stochasticity.STOCHASTIC,
                Stochasticity.UNOBSERVED_STOCHASTIC,
                Stochasticity.UNDEFINED
        );
    }

    protected Tile<?> createInstance() {
        try {
            return this.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Tile " + getClass().getSimpleName() + " has no public no-arg constructor", e);
        }
    }

    public TypeToken<?> getTypeToken() {
        java.lang.reflect.Type rawType = TileUtils.getParametricType(this, 0);
        if (rawType == null) {
            throw new IllegalArgumentException("Tile " + this.getClass() + " has no return type parameter. Either specify the type in the type signature of the inheriting class, or override the getTypeToken method.");
        }
        return TypeToken.of(rawType);
    }

    /** tiling weight */

    private int weight = 0;

    public int getWeight() {
        return this.weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
