package tiles.misc;

import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.BeastXState;
import tiling.model.BeastXTreeDistribution;

import java.util.IdentityHashMap;

public class TreeDrawTile extends AstNodeTile<TreeModel, Stmt.Draw, BeastXState> {

    AstNodeTileInput<BeastXTreeDistribution<?>, Stmt.Draw, BeastXState> expressionInput =
            new AstNodeTileInput<>("expression", stmt -> stmt.expression);

    @Override
    public TreeModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BeastXTreeDistribution<?> evaluatedDistribution =
                this.expressionInput.apply(beastState, indexVariables);

        String id =
                this.getId(this.getRootNode().name, indexVariables, "");

        evaluatedDistribution.bind();

        beastState.addTreePriorDistribution(
                evaluatedDistribution.treeModel,
                evaluatedDistribution.likelihood,
                id
        );

        return evaluatedDistribution.treeModel;
    }
}