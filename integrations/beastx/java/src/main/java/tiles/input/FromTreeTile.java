package tiles.input;

import dr.evolution.io.Importer;
import dr.evolution.io.NewickImporter;
import dr.evolution.io.NexusImporter;
import dr.evolution.tree.Tree;
import dr.evomodel.tree.DefaultTreeModel;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FromTreeTile extends GeneratorTile<DefaultTreeModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromTree";
    }

    GeneratorTileInput<String, BeastXState> fileInput =
            new GeneratorTileInput<>(
                    "file",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public DefaultTreeModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String path =
                this.fileInput.apply(beastState, indexVariables);

        Tree tree;
        if (isNexusPath(path)) {
            tree = importNexusTree(path);
        } else {
            tree = importNewickTree(path);
        }

        return new DefaultTreeModel("tree", tree);
    }

    private static boolean isNexusPath(String path) {
        String lowerPath =
                path.toLowerCase(Locale.ROOT);

        return lowerPath.endsWith(".nex")
                || lowerPath.endsWith(".nexus");
    }

    private static Tree importNewickTree(String path) {
        File file =
                new File(path);

        try (FileReader reader = new FileReader(file)) {
            NewickImporter importer =
                    new NewickImporter(reader);

            return importer.importTree(null);
        } catch (FileNotFoundException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Select a valid tree file path."
            );
        } catch (Importer.ImportException e) {
            throw new TileApplicationError(
                    "Invalid Newick file.",
                    "'" + path + "' could not be parsed as a Newick tree."
            );
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read tree file.",
                    "'" + path + "' could not be read."
            );
        }
    }

    private static Tree importNexusTree(String path) {
        File file =
                new File(path);

        try (FileReader reader = new FileReader(file)) {
            NexusImporter importer =
                    new NexusImporter(reader);

            List<Tree> trees =
                    importer.importTrees(null);

            if (trees.isEmpty()) {
                throw new TileApplicationError(
                        "No tree found.",
                        "'" + path + "' contains no trees. Choose a file with exactly one tree."
                );
            }

            if (trees.size() > 1) {
                throw new TileApplicationError(
                        "Too many trees found.",
                        "'" + path + "' contains more than one tree. Choose a file with exactly one tree."
                );
            }

            return trees.getFirst();
        } catch (FileNotFoundException e) {
            throw new TileApplicationError(
                    "File not found.",
                    "'" + path + "' could not be found. Select a valid tree file path."
            );
        } catch (Importer.ImportException e) {
            throw new TileApplicationError(
                    "Invalid Nexus tree file.",
                    "'" + path + "' could not be parsed as a Nexus tree file."
            );
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read tree file.",
                    "'" + path + "' could not be read."
            );
        }
    }
}
