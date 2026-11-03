package tiles.functions;

import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxonValue;

public class TaxonSpeciesTile extends GeneratorTile<String, BEASTState> {
    @Override
    public String getPhyloSpecGeneratorName() {
        return "species";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    GeneratorTileInput<TaxonValue, BEASTState> taxonInput =
            new GeneratorTileInput<>("taxon", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        TaxonValue taxon = taxonInput.apply(state, indexVariables);
        if (taxon.species() == null) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Species name is not available.",
                    "Taxon '" + taxon.name() + "' has no species name. Supply species in taxon(...) "
                            + "or speciesName when reading the alignment.");
        }
        return taxon.species();
    }
}
