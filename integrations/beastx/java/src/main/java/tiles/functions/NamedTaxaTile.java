package tiles.functions;

import dr.evolution.util.Date;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealVector;
import tiling.BeastXState;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Creates taxa from a list of names, with optional species names and sampling ages given in the
 * same order. This mirrors LPhy's {@code taxa(names=..., species=..., ages=...)}.
 *
 * <p>Sampling ages are stored as relative dates, as {@code taxon(age=...)} and age-annotated
 * alignments do. Without ages the taxa carry no date, so the tree model stays undated.
 */
public class NamedTaxaTile extends GeneratorTile<Taxa, BeastXState> {

    private static final Set<Stochasticity> FIXED =
            Set.of(
                    Stochasticity.CONSTANT,
                    Stochasticity.DETERMINISTIC
            );

    private static final List<String> EXAMPLES =
            List.of("Taxa taxa = taxa(names=[\"a\", \"b\", \"c\"], ages=[0.0, 1.0, 2.0])");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<List<String>, BeastXState> namesInput =
            new GeneratorTileInput<>(
                    "names",
                    FIXED
            );

    GeneratorTileInput<List<String>, BeastXState> speciesInput =
            new GeneratorTileInput<>(
                    "species",
                    false,
                    FIXED
            );

    GeneratorTileInput<RealVector<? extends Real>, BeastXState> agesInput =
            new GeneratorTileInput<>(
                    "ages",
                    false,
                    FIXED
            );

    @Override
    public Taxa applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<String> names =
                this.namesInput.apply(beastState, indexVariables);

        List<String> species =
                this.speciesInput.apply(beastState, indexVariables);

        RealVector<? extends Real> agesVector =
                this.agesInput.apply(beastState, indexVariables);

        double[] ages =
                agesVector == null ? null : agesVector.getDoubleArray();

        if (species != null && species.size() != names.size()) {
            throw this.lengthMismatch("species", species.size(), names.size());
        }

        if (ages != null && ages.length != names.size()) {
            throw this.lengthMismatch("ages", ages.length, names.size());
        }

        Set<String> seenNames =
                new HashSet<>();

        Taxa taxa =
                new Taxa();

        for (int i = 0; i < names.size(); i++) {
            String name =
                    names.get(i);

            if (name == null || name.isBlank()) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "Invalid taxon name.",
                        "Every taxon needs a nonblank name.",
                        EXAMPLES
                );
            }

            if (!seenNames.add(name)) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "Duplicate taxon name.",
                        "Taxon names must be unique; '" + name + "' occurs more than once.",
                        EXAMPLES
                );
            }

            Taxon taxon =
                    new Taxon(name);

            if (species != null) {
                taxon.setAttribute(
                        "species",
                        species.get(i)
                );
            }

            if (ages != null) {
                double age =
                        ages[i];

                if (!Double.isFinite(age) || age < 0.0) {
                    throw new TileApplicationError(
                            this.getRootNode(),
                            "Invalid sampling age.",
                            "The sampling age of '" + name + "' must be finite and nonnegative.",
                            EXAMPLES
                    );
                }

                taxon.setDate(
                        Date.createRelativeAge(
                                age,
                                Units.Type.YEARS
                        )
                );
            }

            taxa.addTaxon(taxon);
        }

        return taxa;
    }

    private TileApplicationError lengthMismatch(
            String argument,
            int actual,
            int expected
    ) {
        return new TileApplicationError(
                this.getRootNode(),
                "The '" + argument + "' argument of taxa has the wrong length.",
                "Provide one entry per name: expected " + expected + " but got " + actual + ".",
                EXAMPLES
        );
    }
}
