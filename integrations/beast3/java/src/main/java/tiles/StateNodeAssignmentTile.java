package tiles;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.AstNodeTile;
import patternmatching.EvaluatedTile;
import patternmatching.TypeToken;

import java.util.Map;
import java.util.Set;

public class StateNodeAssignmentTile extends AstNodeTile<Stmt.Assignment> {

    TileInput<Stmt.Assignment, ? extends StateNode> evaluatedDistributionInput = new TileInput<>(
            expr -> expr.expression,
            new TypeToken<>() {}
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public Set<EvaluatedTile> applyTile(Stmt.Assignment expr) {
        StateNode stateNode = this.evaluatedDistributionInput.get();

        // set the variable name as the ID of the state node

        stateNode.setID(expr.name);

        // we now return the state node as the generated object while adding it to the state

        return Set.of(
                new EvaluatedTile(
                        this,
                        stateNode,
                        evaluatedDistributionInput.getType(),
                        Set.of(stateNode),
                        Map.of(),
                        Set.of()
                )
        );
    }

}
