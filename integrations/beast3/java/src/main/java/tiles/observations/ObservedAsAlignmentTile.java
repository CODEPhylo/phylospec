package tiles.observations;

import beast.base.evolution.alignment.Alignment;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.TemplateTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;

/**
 * This tile is the same as {@code ObservedAsTile}, except that we expect a DecoratedAlignment for the observed value
 * instead of a StateNode.
 */
public class ObservedAsAlignmentTile extends TemplateTile<DecoratedAlignment, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends Alignment, ?>, BEASTState> distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<DecoratedAlignment, BEASTState> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        // this is the same as ObservedAsTile, except that we unwrap the alignment object from the DecoratedAlignment

        UnboundDistribution<? extends Alignment, ?> evaluatedDistribution = this.distributionInput.apply(beastState, indexVariables);
        DecoratedAlignment observedStateNode = this.observationInput.apply(beastState, indexVariables);

        // find the ID

        String prefix = "";

        if (this.getRootNode() instanceof Stmt stmt) {
            prefix = stmt.getName();
        } else if (observedStateNode.alignment() != null) {
            prefix = observedStateNode.alignment().getID();
        }

        String id = this.getId(prefix, indexVariables, "likelihood");

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode.alignment()); // here we unwrap observedStateNode
        beastState.addLikelihoodDistribution(evaluatedDistribution.distribution, id);

        // we return the observed state

        return observedStateNode;
    }

}
