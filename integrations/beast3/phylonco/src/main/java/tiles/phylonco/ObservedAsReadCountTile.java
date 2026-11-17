package tiles.phylonco;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.UnboundDistribution;

/** {@code ReadCount x ~ ReadCountModel(...) observed as readCounts}: registers the read-count likelihood. */
public class ObservedAsReadCountTile extends TemplateTile<ReadCountData, BEASTState> {

    TemplateTileInput<UnboundDistribution<ReadCountData, ?>, BEASTState> distributionInput =
            new TemplateTileInput<>("$distribution");
    TemplateTileInput<ReadCountData, BEASTState> observationInput = new TemplateTileInput<>("$observation");

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    @Override
    public ReadCountData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        UnboundDistribution<ReadCountData, ?> distribution = this.distributionInput.apply(state, indices);
        ReadCountData observed = this.observationInput.apply(state, indices);
        String prefix = this.getRootNode() instanceof Stmt stmt ? stmt.getName() : "readCounts";
        distribution.observeAs(state, observed, this.getId(prefix, indices, "likelihood"));
        return observed;
    }
}
