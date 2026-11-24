package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.TilePriority;
import tiling.BeastXState;
import tiling.params.BeastXStatisticRealScalar;

import java.util.IdentityHashMap;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment, BeastXState> {

    AstNodeTileInput<Object, Stmt.Assignment, BeastXState> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Object applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Object value =
                this.expressionInput.apply(beastState, indexVariables);

        // A live value such as Age h = mrca(...) can be logged by its PhyloSpec name.
        if (value instanceof BeastXStatisticRealScalar<?> statistic
                && this.getRootNode() instanceof Stmt.Assignment assignment) {
            beastState.calculationNodesByPhyloSpecName.putIfAbsent(assignment.name, statistic.getStatistic());
        }

        return value;
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }
}