package tiles;

import org.phylospec.ast.Stmt;
import patternmatching.AstNodeTile;
import patternmatching.EvaluatedTile;
import patternmatching.TilePriority;
import patternmatching.TypeToken;

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

        // we now return the state node without doing anything

        return Set.of(
                new EvaluatedTile(
                        this,
                        input,
                        evaluatedDistributionInput.getType(),
                        Set.of(),
                        Set.of(),
                        Set.of()
                )
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

}
