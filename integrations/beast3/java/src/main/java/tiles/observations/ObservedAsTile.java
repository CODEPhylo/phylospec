package tiles.observations;

import beast.base.inference.StateNode;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.lang.reflect.ParameterizedType;
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
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> candidates =
                super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
        // The two wildcard inputs must agree with each other, not just with StateNode.
        candidates.removeIf(candidate -> !((ObservedAsTile) candidate).hasCompatibleObservation());
        if (candidates.isEmpty()) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    "Your engine cannot bind the observation to the value type required by this distribution.");
        }
        return candidates;
    }

    private boolean hasCompatibleObservation() {
        TypeToken<?> expected = TypeToken.firstConcreteTypeArg(this.distributionInput.getTypeToken());
        TypeToken<?> actual = this.observationInput.getTypeToken();
        if (expected == null) return false;
        if (expected.isAssignableFrom(actual)) return true;

        // Observed scalars are read-only. Preserve narrower numeric domains (e.g. a
        // PositiveReal result observed under a Real distribution) without accepting
        // a different scalar shape or a wider domain. Global generic rules stay invariant.
        if (expected.getType() instanceof ParameterizedType expectedScalar
                && actual.getType() instanceof ParameterizedType actualScalar
                && (expectedScalar.getRawType() == RealScalarParam.class
                        || expectedScalar.getRawType() == IntScalarParam.class)
                && expectedScalar.getRawType().equals(actualScalar.getRawType())) {
            return TypeToken.of(expectedScalar.getActualTypeArguments()[0])
                    .isAssignableFrom(TypeToken.of(actualScalar.getActualTypeArguments()[0]));
        }
        return false;
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
