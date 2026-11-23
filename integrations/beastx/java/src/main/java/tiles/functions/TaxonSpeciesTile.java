package tiles.functions;

import dr.evolution.util.Taxon;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class TaxonSpeciesTile extends GeneratorTile<String, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "species";
    }

    GeneratorTileInput<Taxon, BeastXState> taxonInput =
            new GeneratorTileInput<>("taxon");

    @Override
    public String applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Taxon taxon =
                this.taxonInput.apply(beastState, indexVariables);

        // Set by taxon(name, species=...) or by the speciesName parser of fromNexus/fromFasta.
        Object species =
                taxon.getAttribute("species");

        if (species == null) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Taxon '" + taxon.getId() + "' has no species name.",
                    "Give the species in taxon(...), or use speciesName when reading the alignment.",
                    List.of("Alignment data = fromNexus(file=\"data.nex\", speciesName=parse(delimiter=\"_\", part=1))")
            );
        }

        return species.toString();
    }
}
