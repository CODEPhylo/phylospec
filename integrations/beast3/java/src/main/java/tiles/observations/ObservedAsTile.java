package tiles.observations;

import beast.base.inference.StateNode;
import org.phylospec.typeresolver.Stochasticity;
import tiles.MultiAstNodeTile;
import tiling.*;

import java.util.Set;

public class ObservedAsTile extends MultiAstNodeTile<StateNode> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    MultiAstNodeTileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new MultiAstNodeTileInput<>("$distribution");
    MultiAstNodeTileInput<? extends StateNode> observationInput = new MultiAstNodeTileInput<>("$observation");

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

}
