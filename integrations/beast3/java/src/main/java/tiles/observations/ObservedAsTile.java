package tiles.observations;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.UnboundDistribution;

public class ObservedAsTile extends TemplateTile<StateNode, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<? extends StateNode, ?>, BEASTState> distributionInput =
            new TemplateTileInput<>("$distribution");
    TemplateTileInput<? extends StateNode, BEASTState> observationInput = new TemplateTileInput<>("$observation");

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> candidates =
                super.tryToTile(node, inputTiles, variableResolver, stochasticityResolver);
        candidates.removeIf(candidate -> {
            ObservedAsTile observation = (ObservedAsTile) candidate;
            TypeToken<?> valueType = TypeToken.firstConcreteTypeArg(observation.distributionInput.getTypeToken());
            // Both inputs accept StateNode independently, but the distribution's binding
            // callback requires its specific value type (including the parameter domain).
            return valueType != null && !valueType.isAssignableFrom(observation.observationInput.getTypeToken());
        });
        if (candidates.isEmpty()) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    "The observed value has no BEAST representation compatible with the distribution's value type.");
        }
        return candidates;
    }

    @Override
    public StateNode applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        UnboundDistribution<? extends StateNode, ?> evaluatedDistribution =
                this.distributionInput.apply(beastState, indexVariables);
        StateNode observedStateNode = this.observationInput.apply(beastState, indexVariables);

        // find the ID

        String prefix = "";

        if (this.getRootNode() instanceof Stmt stmt) {
            prefix = stmt.getName();
        } else if (observedStateNode.getID() != null) {
            prefix = observedStateNode.getID();
        }

        String id = this.getId(prefix, indexVariables, "likelihood");

        // we register the distribution as a likelihood with the given state node as parameter

        evaluatedDistribution.observeAs(beastState, observedStateNode, id);

        // we return the observed state

        return observedStateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.observationInput.getTypeToken();
    }
}
