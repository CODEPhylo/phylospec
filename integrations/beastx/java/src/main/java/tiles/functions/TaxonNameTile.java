package tiles.functions;

import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class TaxonNameTile extends GeneratorTile<String, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "name";
    }

    GeneratorTileInput<Taxon, BeastXState> taxonInput =
            new GeneratorTileInput<>("taxon");

    @Override
    public String applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        return this.taxonInput.apply(beastState, indexVariables).getId();
    }
}
