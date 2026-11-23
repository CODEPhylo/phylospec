package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.io.Importer;
import dr.evolution.io.NexusImporter;
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

    GeneratorTileInput<ParserTile.Parser, BeastXState> speciesNameInput =
            new GeneratorTileInput<>("speciesName", false);

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

        ParserTile.Parser speciesNameParser =
                this.speciesNameInput.apply(beastState, indexVariables);

        File file =
                beastState.resolveInput(path).toFile();

        try (FileReader reader = new FileReader(file)) {
            NexusImporter importer =
                    new NexusImporter(reader);

            Alignment alignment =
                    importer.importAlignment();

            TaxonMetadata.apply(alignment, ageParser, dateParser, speciesNameParser, "fromNexus");

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
}
