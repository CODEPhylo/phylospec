package tiles.input;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.parser.NexusParser;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TileApplicationError;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Set;

public class FromTreeTile extends GeneratorTile<Tree> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromTree";
    }

    GeneratorTileInput<String> fileInput = new GeneratorTileInput<>(
            "file", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public Tree applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String path = this.fileInput.apply(beastState, indexVariables);
        if (path.endsWith(".nex") || path.endsWith(".nexus") || path.endsWith(".trees")) {
            NexusParser nexusParser = new NexusParser();
            File file = new File(path);

            try {
                nexusParser.parseFile(file);
            } catch (IOException e) {
                throw new TileApplicationError(
                        "File not found.",
                        "'" + path + "' could not be found. Does it exist? Select a valid file path."
                );
            }

            if (nexusParser.trees == null || nexusParser.trees.isEmpty()) {
                throw new TileApplicationError(
                        "No tree found.",
                        "The file '" + path + "' contains no trees. Choose a file with exactly one tree to load."
                );
            }
            if (nexusParser.trees.size() > 1) {
                throw new TileApplicationError(
                        "Too many trees found.",
                        "The file '" + path + "' contains more than one tree. Choose a file with exactly one tree to load."
                );
            }

            return nexusParser.trees.getFirst();
        }

        // we assume that we are working with a Newick file

        String newick = null;
        try {
            newick = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Does it exist? Select a valid file path."
            );
        }
        return new TreeParser(newick);
    }

}
