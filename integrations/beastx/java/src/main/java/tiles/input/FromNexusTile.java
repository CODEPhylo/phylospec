package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.io.Importer;
import dr.evolution.io.NexusImporter;
import dr.evolution.util.Date;
import dr.evolution.util.Taxon;
import dr.evolution.util.Units;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;

public class FromNexusTile extends GeneratorTile<Alignment, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNexus";
    }

    GeneratorTileInput<String, BeastXState> fileInput =
            new GeneratorTileInput<>(
                    "file",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<ParserTile.Parser, BeastXState> ageInput =
            new GeneratorTileInput<>("age", false);

    GeneratorTileInput<ParserTile.Parser, BeastXState> dateInput =
            new GeneratorTileInput<>("date", false);

    @Override
    public Alignment applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String path =
                this.fileInput.apply(beastState, indexVariables);

        ParserTile.Parser ageParser =
                this.ageInput.apply(beastState, indexVariables);

        ParserTile.Parser dateParser =
                this.dateInput.apply(beastState, indexVariables);

        if (ageParser != null && dateParser != null) {
            throw new TileApplicationError(
                    "fromNexus cannot use both age and date parsers.",
                    "Use either age=parse(...) or date=parse(...), not both."
            );
        }

        if (dateParser != null) {
            throw new TileApplicationError(
                    "fromNexus date parsing is not implemented for BEAST X yet.",
                    "Use age=parse(...) for BEAST X tip ages."
            );
        }

        File file =
                new File(path);

        try (FileReader reader = new FileReader(file)) {
            NexusImporter importer =
                    new NexusImporter(reader);

            Alignment alignment =
                    importer.importAlignment();

            if (ageParser != null) {
                applyTaxonAges(alignment, ageParser);
            }

            return alignment;
        } catch (FileNotFoundException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Select a valid Nexus file path."
            );
        } catch (Importer.ImportException e) {
            throw new TileApplicationError(
                    "Invalid Nexus file.",
                    "'" + path + "' could not be parsed as a Nexus alignment."
            );
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read Nexus file.",
                    "'" + path + "' could not be read."
            );
        }
    }

    private static void applyTaxonAges(
            Alignment alignment,
            ParserTile.Parser ageParser
    ) {
        for (int i = 0; i < alignment.getTaxonCount(); i++) {
            Taxon taxon =
                    alignment.getTaxon(i);

            String parsedAge =
                    ageParser.parse(taxon.getId());

            double age =
                    parseAge(taxon.getId(), parsedAge);

            taxon.setDate(
                    Date.createRelativeAge(
                            age,
                            Units.Type.YEARS
                    )
            );
        }
    }

    private static double parseAge(
            String taxonName,
            String parsedAge
    ) {
        try {
            double age =
                    Double.parseDouble(parsedAge);

            if (age < 0.0) {
                throw new TileApplicationError(
                        "Parsed taxon age is negative for taxon '" + taxonName + "'.",
                        "Use an age parser that returns non-negative numeric ages."
                );
            }

            return age;
        } catch (NumberFormatException e) {
            throw new TileApplicationError(
                    "Parsed taxon age is not numeric for taxon '" + taxonName + "'.",
                    "Check the age parser. It returned '" + parsedAge + "'."
            );
        }
    }
}