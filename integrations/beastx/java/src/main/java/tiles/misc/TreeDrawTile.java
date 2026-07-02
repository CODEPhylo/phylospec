package tiles.misc;

import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.BeastXState;
import tiling.model.TreeDistribution;

import java.util.IdentityHashMap;

public class TreeDrawTile extends AstNodeTile<TreeModel, Stmt.Draw, BeastXState> {

    AstNodeTileInput<TreeDistribution<?>, Stmt.Draw, BeastXState> expressionInput =
            new AstNodeTileInput<>("expression", stmt -> stmt.expression);

    @Override
    public TreeModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String id =
                this.getId(this.getRootNode().name, indexVariables, "");

        TreeModel existingTreeModel =
                beastState.treeModelsByPhyloSpecName.get(id);

        if (existingTreeModel != null) {
            return existingTreeModel;
        }

        TreeDistribution<?> evaluatedDistribution =
                this.expressionInput.apply(beastState, indexVariables);

        evaluatedDistribution.bind();

        return beastState.addTreePriorDistribution(
                evaluatedDistribution.treeModel,
                evaluatedDistribution.likelihood,
                id,
                evaluatedDistribution.startingTreeSpec
        );
    }
}
