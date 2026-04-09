package tiles.observations;

import beast.base.evolution.alignment.Alignment;
import beast.base.inference.StateNode;
import tiles.MultiAstNodeTile;
import tiles.input.DecoratedAlignment;
import tiling.BEASTState;
import tiling.TypeToken;
import tiling.UnboundDistribution;

public class ObservedAsAlignmentTile extends MultiAstNodeTile<Alignment> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TileInput<>("$distribution");
    TileInput<DecoratedAlignment> observationInput = new TileInput<>("$observation");

    @Override
    public Alignment applyTile(BEASTState beastState) {
        // this is the same as ObservedAsTile, expect that we unwrap the alignment object from the DecoratedAlignment

        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState);
        Alignment observedStateNode = this.observationInput.apply(beastState).alignment();

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

}
