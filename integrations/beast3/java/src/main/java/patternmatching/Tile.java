package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

public abstract class Tile<T> {
    public abstract Set<Tile<T>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, TypeResolver typeResolver, VariableResolver variableResolver);

    public abstract T applyTile(BEASTState beastState);

    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    protected abstract Tile<T> createInstance();

    public abstract Type getGeneratedType();

    private int weight = 0;

    public int getWeight() {
        return this.weight;
    }

    protected void setWeight(int weight) {
        this.weight = weight;
    }
}
