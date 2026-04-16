package tiles.observations;

import beast.base.inference.StateNode;
import tiles.TemplateTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.UnboundDistribution;

public class ObservedAsAlignmentTile extends TemplateTile<DecoratedAlignment> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<DecoratedAlignment> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState) {
        // this is the same as ObservedAsTile, expect that we unwrap the alignment object from the DecoratedAlignment

        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState);
        DecoratedAlignment observedStateNode = this.observationInput.apply(beastState);

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode.alignment());
        beastState.addDistribution(observedStateNode.alignment(), evaluatedDistribution.distribution, "likelihood");

        // we return the observed state

        return observedStateNode;
    }

}
