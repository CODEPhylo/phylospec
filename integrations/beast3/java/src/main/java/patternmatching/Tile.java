package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.util.Map;
import java.util.Set;

public abstract class Tile<T> {
    public abstract Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, TypeResolver typeResolver, VariableResolver variableResolver);

    public abstract T applyTile(BEASTState beastState);

    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    protected abstract Tile<?> createInstance();

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

    protected void setWeight(int weight) {
        this.weight = weight;
    }
}
