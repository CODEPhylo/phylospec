package tiles;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.*;

import java.lang.reflect.Type;

public class StateNodeAssignmentTile<T extends StateNode> extends AstNodeTile<StateNode, Stmt.Assignment> {

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
        T value = this.evaluatedDistributionInput.apply(beastState);

        // add to state
        beastState.addStateNode(value);

        return value;
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    protected Tile<StateNode> createInstance() {
        return new AssignmentTile<>();
    }

    @Override
    public Type getGeneratedType() {
        return new TypeToken<T>() {}.getType();
    }

}
