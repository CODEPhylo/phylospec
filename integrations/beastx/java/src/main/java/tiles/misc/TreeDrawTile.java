package tiles.misc;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;

public class TreeDrawTile extends AstNodeTile<TreeModel, Stmt.Draw, BeastXState> {

    AstNodeTileInput<
            BoundDistribution<? extends TreeModel, ? extends AbstractModelLikelihood>,
            Stmt.Draw,
            BeastXState
            > expressionInput =
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

        BoundDistribution<? extends TreeModel, ? extends AbstractModelLikelihood> evaluatedDistribution =
                this.expressionInput.apply(beastState, indexVariables);

        evaluatedDistribution.bind();

        return beastState.addTreePriorDistribution(
                evaluatedDistribution.stateNode,
                evaluatedDistribution.distribution,
                evaluatedDistribution.startingTreeSpec,
                id
        );
    }
}
