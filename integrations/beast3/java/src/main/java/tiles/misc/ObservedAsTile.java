package tiles.misc;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import tiles.AstNodeTile;
import tiles.MultiAstNodeTile;
import tiling.*;

public class ObservedAsTile extends MultiAstNodeTile<StateNode> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TileInput<EvaluatedDistribution<? extends StateNode, ?>> distributionInput = new TileInput<>("$distribution");
    TileInput<? extends StateNode> observationInput = new TileInput<>("$observation");

    @Override
    public StateNode applyTile(BEASTState beastState) {
        EvaluatedDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState);
        StateNode observedStateNode = this.observationInput.apply(beastState);

        // we ask the distribution to register itself as a likelihood with the given state node as parameter

        evaluatedDistribution.initializeAsLikelihoodOfState(observedStateNode, beastState);

        // we return the observed state

        return observedStateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.observationInput.getTypeToken();
    }

    @Override
    protected Tile<?> createInstance() {
        return new ObservedAsTile();
    }

}
