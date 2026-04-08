package tiles.misc;

import beast.base.inference.StateNode;
import tiles.MultiAstNodeTile;
import tiling.*;

public class ObservedAsTile extends MultiAstNodeTile<StateNode> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TileInput<>("$distribution");
    TileInput<? extends StateNode> observationInput = new TileInput<>("$observation");

    @Override
    public StateNode applyTile(BEASTState beastState) {
        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState);
        StateNode observedStateNode = this.observationInput.apply(beastState);

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode);
        beastState.addDistribution(observedStateNode, evaluatedDistribution.distribution, "likelihood");

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
