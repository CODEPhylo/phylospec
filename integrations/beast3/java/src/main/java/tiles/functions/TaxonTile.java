package tiles.functions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxonValue;

public class TaxonTile extends GeneratorTile<TaxonValue, BEASTState> {
    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxon";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    GeneratorTileInput<String, BEASTState> nameInput =
            new GeneratorTileInput<>("name", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<String, BEASTState> speciesInput =
            new GeneratorTileInput<>("species", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BEASTState> ageInput =
            new GeneratorTileInput<>("age", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public TaxonValue applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String name = nameInput.apply(state, indexVariables);
        String species = speciesInput.apply(state, indexVariables);
        RealScalar<? extends NonNegativeReal> age = ageInput.apply(state, indexVariables);
        try {
            return new TaxonValue(name, species, age == null ? 0.0 : age.get());
        } catch (IllegalArgumentException e) {
            throw new TileApplicationError(getRootNode(), "Invalid taxon.", e.getMessage());
        }
    }
}
