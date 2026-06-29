package tiles.functions;

import dr.evolution.util.Date;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class TaxonTile extends GeneratorTile<Taxon, BeastXState> {

    private static final Set<Stochasticity> NON_STOCHASTIC =
            Set.of(
                    Stochasticity.CONSTANT,
                    Stochasticity.DETERMINISTIC
            );

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxon";
    }

    GeneratorTileInput<String, BeastXState> nameInput =
            new GeneratorTileInput<>(
                    "name",
                    NON_STOCHASTIC
            );

    GeneratorTileInput<String, BeastXState> speciesInput =
            new GeneratorTileInput<>(
                    "species",
                    false,
                    NON_STOCHASTIC
            );

    GeneratorTileInput<RealScalar<NonNegativeReal>, BeastXState> ageInput =
            new GeneratorTileInput<>(
                    "age",
                    false,
                    NON_STOCHASTIC
            );

    @Override
    public Taxon applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String name =
                this.nameInput.apply(beastState, indexVariables);

        String species =
                this.speciesInput.apply(beastState, indexVariables);

        RealScalar<NonNegativeReal> age =
                this.ageInput.apply(beastState, indexVariables);

        Taxon taxon =
                new Taxon(name);

        if (species != null) {
            taxon.setAttribute(
                    "species",
                    species
            );
        }

        double ageValue =
                age == null ? 0.0 : age.get();

        taxon.setDate(
                Date.createRelativeAge(
                        ageValue,
                        Units.Type.YEARS
                )
        );

        return taxon;
    }
}