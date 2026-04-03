package tiles;

import org.phylospec.ast.Stmt;
import patternmatching.*;

import java.lang.reflect.Type;

public class AssignmentTile<T> extends AstNodeTile<T, Stmt.Assignment> {

    TileInput<Stmt.Assignment, T> evaluatedDistributionInput = new TileInput<>(
            expr -> expr.expression,
            new TypeToken<>() {}
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public T applyTile(Stmt.Assignment node, BEASTState beastState) {
        return this.evaluatedDistributionInput.apply(beastState);
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    protected Tile<T> createInstance() {
        return new AssignmentTile<>();
    }

    @Override
    public Type getGeneratedType() {
        return new TypeToken<T>() {}.getType();
    }

}
