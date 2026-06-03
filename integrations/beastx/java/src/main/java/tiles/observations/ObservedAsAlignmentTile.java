package tiles.observations;

import dr.evolution.alignment.Alignment;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.BeastXState;
import tiling.model.UnboundDistribution;

import java.util.IdentityHashMap;

public class ObservedAsAlignmentTile extends TemplateTile<Alignment, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<UnboundDistribution<Alignment>, BeastXState> distributionInput =
            new TemplateTileInput<>("$distribution");

    TemplateTileInput<Alignment, BeastXState> observationInput =
            new TemplateTileInput<>("$observation");

    @Override
    public Alignment applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        UnboundDistribution<Alignment> distribution =
                this.distributionInput.apply(beastState, indexVariables);

        Alignment observedAlignment =
                this.observationInput.apply(beastState, indexVariables);

        String prefix = "";
        if (this.getRootNode() instanceof Stmt stmt) {
            prefix = stmt.getName();
        }

        String id =
                this.getId(prefix, indexVariables, "likelihood");

        distribution.bind(observedAlignment, id);

        return observedAlignment;
    }
}