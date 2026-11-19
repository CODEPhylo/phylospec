package tiles.functions;

import beast.base.spec.domain.Real;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;

/**
 * Creates taxa from a list of names, with optional species names and sampling ages given in the
 * same order. This mirrors LPhy's {@code taxa(names=..., species=..., ages=...)}.
 */
public class NamedTaxaTile extends GeneratorTile<List<TaxonValue>, BEASTState> {

    private static final Set<Stochasticity> FIXED = Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    GeneratorTileInput<List<String>, BEASTState> namesInput = new GeneratorTileInput<>("names", FIXED);
    GeneratorTileInput<List<String>, BEASTState> speciesInput = new GeneratorTileInput<>("species", false, FIXED);
    GeneratorTileInput<RealVector<? extends Real>, BEASTState> agesInput =
            new GeneratorTileInput<>("ages", false, FIXED);

    @Override
    public TaxaData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<String> names = namesInput.apply(state, indexVariables);
        List<String> species = speciesInput.apply(state, indexVariables);
        RealVector<? extends Real> ages = agesInput.apply(state, indexVariables);

        if (species != null && species.size() != names.size()) {
            throw lengthMismatch("species", species.size(), names.size());
        }
        if (ages != null && ages.size() != names.size()) {
            throw lengthMismatch("ages", ages.size(), names.size());
        }

        List<TaxonValue> values = new ArrayList<>(names.size());
        try {
            for (int i = 0; i < names.size(); i++) {
                values.add(new TaxonValue(
                        names.get(i), species == null ? null : species.get(i), ages == null ? 0.0 : ages.get(i)));
            }
        } catch (IllegalArgumentException e) {
            throw new TileApplicationError(getRootNode(), "Invalid taxon.", e.getMessage());
        }
        return TaxaData.fromList(state, values);
    }

    private TileApplicationError lengthMismatch(String argument, int actual, int expected) {
        return new TileApplicationError(
                getRootNode(),
                "The '" + argument + "' argument of taxa has the wrong length.",
                "Provide one entry per name: expected " + expected + " but got " + actual + ".");
    }
}
