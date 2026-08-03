package tiles.observations;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;

/**
 * Evaluates a BEAST X tree distribution on a fixed observed tree.
 */
public class ObservedAsTreeTile extends TemplateTile<TreeModel, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<
            BoundDistribution<TreeModel, ? extends AbstractModelLikelihood>,
            BeastXState
            > distributionInput =
            new TemplateTileInput<>("$distribution");

    TemplateTileInput<TreeModel, BeastXState> observationInput =
            new TemplateTileInput<>("$observation");

    @Override
    public TreeModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<TreeModel, ? extends AbstractModelLikelihood> distribution =
                this.distributionInput.apply(beastState, indexVariables);

        TreeModel observedTree =
                this.observationInput.apply(beastState, indexVariables);

        String treeId =
                this.getRootNode() instanceof Stmt stmt
                        ? this.getId(stmt.getName(), indexVariables, "")
                        : this.getId("tree", indexVariables, "");

        String likelihoodId =
                treeId + "_likelihood";

        distribution.bind(observedTree);

        return beastState.addObservedTreeDistribution(
                observedTree,
                distribution.distribution,
                treeId,
                likelihoodId
        );
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.observationInput.getTypeToken();
    }
}
