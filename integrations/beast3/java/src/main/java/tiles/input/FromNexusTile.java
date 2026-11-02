package tiles.input;

import beast.base.parser.NexusParser;
import beastconfig.BEASTState;
import java.io.File;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class FromNexusTile extends GeneratorTile<DecoratedAlignment, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNexus";
    }

    GeneratorTileInput<String, BEASTState> fileInput =
            new GeneratorTileInput<>("file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<ParserTile.Parser, BEASTState> ageInput = new GeneratorTileInput<>("age", false);
    GeneratorTileInput<ParserTile.Parser, BEASTState> dateInput = new GeneratorTileInput<>("date", false);
    GeneratorTileInput<ParserTile.Parser, BEASTState> speciesNameInput =
            new GeneratorTileInput<>("speciesName", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public DecoratedAlignment applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String path = this.fileInput.apply(beastState, indexVariables);
        File file = beastState.resolvePath(path).toFile();

        NexusParser nexusParser = new NexusParser();
        try {
            nexusParser.parseFile(file);
        } catch (IOException e) {
            throw new TileApplicationError(
                    getRootNode(),
                    "Could not read input file.",
                    "'" + path + "' resolves to '" + file + "'. " + e.getMessage());
        }

        return DecoratedAlignment.create(
                beastState,
                nexusParser.m_alignment,
                this.ageInput.apply(beastState, indexVariables),
                this.dateInput.apply(beastState, indexVariables),
                this.speciesNameInput.apply(beastState, indexVariables),
                file.toPath());
    }
}
