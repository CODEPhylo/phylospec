package tiles;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.AstNodeTile;
import patternmatching.EvaluatedTile;
import patternmatching.TilePriority;
import patternmatching.TypeToken;

import java.util.Map;
import java.util.Set;

public class AssignmentTile extends AstNodeTile<Stmt.Assignment> {

    TileInput<Stmt.Assignment, Object> evaluatedDistributionInput = new TileInput<>(
            expr -> expr.expression,
            new TypeToken<>() {}
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public Set<EvaluatedTile> applyTile(Stmt.Assignment expr) {
        Object input = this.evaluatedDistributionInput.get();

        // set the variable name as the ID of the state node

        if (input instanceof StateNode stateNode) {
            stateNode.setID(expr.name);
        }

        // return the state node

        return Set.of(
                new EvaluatedTile(
                        this,
                        input,
                        evaluatedDistributionInput.getType(),
                        Set.of(),
                        Map.of(),
                        Set.of()
                )
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

}
