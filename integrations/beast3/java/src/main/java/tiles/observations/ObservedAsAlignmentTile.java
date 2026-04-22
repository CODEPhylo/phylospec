package tiles.observations;

import beast.base.inference.StateNode;
import tiles.TemplateTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.UnboundDistribution;

import java.util.Map;

public class ObservedAsAlignmentTile extends TemplateTile<DecoratedAlignment> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<DecoratedAlignment> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        // this is the same as ObservedAsTile, expect that we unwrap the alignment object from the DecoratedAlignment

        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState, indexVariables);
        DecoratedAlignment observedStateNode = this.observationInput.apply(beastState, indexVariables);

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode.alignment());
        beastState.addLikelihoodDistribution(
                observedStateNode.alignment(), evaluatedDistribution.distribution, observedStateNode.alignment().getID() + "_likelihood"
        );

        // we return the observed state

        return observedStateNode;
    }

}
