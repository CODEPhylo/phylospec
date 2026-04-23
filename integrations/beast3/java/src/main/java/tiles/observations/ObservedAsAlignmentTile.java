package tiles.observations;

import beast.base.inference.StateNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import tiles.TemplateTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

public class ObservedAsAlignmentTile extends TemplateTile<DecoratedAlignment> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends StateNode, ?>> distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<DecoratedAlignment> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        // this is the same as ObservedAsTile, expect that we unwrap the alignment object from the DecoratedAlignment

        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution = this.distributionInput.apply(beastState, indexVariables);
        DecoratedAlignment observedStateNode = this.observationInput.apply(beastState, indexVariables);

        // find the ID

        StringBuilder id = new StringBuilder();

        if (this.getRootNode() instanceof Stmt stmt) {
            id.append(stmt.getName()).append("_");
        } else if (observedStateNode.alignment() != null) {
            id.append(observedStateNode.alignment()).append("_");
        }

        if (!indexVariables.isEmpty()) {
            Map<String, String> sortedIndexValues = new TreeMap<>();
            for (Expr.Variable indexVar : indexVariables.keySet()) {
                // this does not work with duplicate index names, but this never happens
                sortedIndexValues.put(indexVar.variableName, indexVariables.get(indexVar).toString());
            }

            for (String index : sortedIndexValues.keySet()) {
                id.append(sortedIndexValues.get(index)).append("_");
            }
        }

        id.append("likelihood");

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.bind(observedStateNode.alignment());
        beastState.addLikelihoodDistribution(evaluatedDistribution.distribution, id.toString());

        // we return the observed state

        return observedStateNode;
    }

}
