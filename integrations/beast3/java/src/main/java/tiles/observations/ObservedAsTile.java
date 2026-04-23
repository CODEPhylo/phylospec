package tiles.observations;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import tiles.TemplateTile;
import tiling.*;

import java.util.IdentityHashMap;

public class ObservedAsTile extends TemplateTile<StateNode> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<? extends StateNode> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public StateNode applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState, indexVariables);
        StateNode observedStateNode = this.observationInput.apply(beastState, indexVariables);

        // find the ID

        String id = "likelihood";
        if (this.getRootNode() instanceof Stmt stmt) {
            id = stmt.getName() + "_likelihood";
        } else if (observedStateNode.getID() != null) {
            id = observedStateNode.getID() + "_likelihood";
        }

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode);
        beastState.addLikelihoodDistribution(evaluatedDistribution.distribution, id);

        // we return the observed state

        return observedStateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.observationInput.getTypeToken();
    }

}
