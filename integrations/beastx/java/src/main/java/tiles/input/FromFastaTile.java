package tiles.input;

import dr.evolution.alignment.Alignment;
import dr.evolution.datatype.Nucleotides;
import dr.evolution.io.FastaImporter;
import dr.evolution.io.Importer;
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

public class FromFastaTile extends GeneratorTile<Alignment, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromFasta";
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
        String path = this.fileInput.apply(beastState, indexVariables);
        ParserTile.Parser ageParser = this.ageInput.apply(beastState, indexVariables);
        ParserTile.Parser dateParser = this.dateInput.apply(beastState, indexVariables);
        ParserTile.Parser speciesNameParser = this.speciesNameInput.apply(beastState, indexVariables);
        File file = beastState.resolveInput(path).toFile();

        try (FileReader reader = new FileReader(file)) {
            FastaImporter importer =
                    new FastaImporter(reader, Nucleotides.INSTANCE);

            Alignment alignment = importer.importAlignment();
            TaxonMetadata.apply(alignment, ageParser, dateParser, speciesNameParser, "fromFasta");
            return alignment;
        } catch (FileNotFoundException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Select a valid FASTA file path."
            );
        } catch (Importer.ImportException e) {
            throw new TileApplicationError(
                    "Invalid FASTA file.",
                    "'" + path + "' could not be parsed as a nucleotide FASTA alignment."
            );
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read FASTA file.",
                    "'" + path + "' could not be read."
            );
        }
    }
}
