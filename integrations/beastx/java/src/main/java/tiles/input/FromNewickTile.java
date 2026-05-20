package tiles.input;

import dr.evolution.io.Importer;
import dr.evolution.io.NewickImporter;
import dr.evolution.tree.Tree;
import dr.evomodel.tree.DefaultTreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;

public class FromNewickTile extends GeneratorTile<DefaultTreeModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNewick";
    }

    GeneratorTileInput<String, BeastXState> newickStringInput =
            new GeneratorTileInput<>(
                    "newickString",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public DefaultTreeModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String newick =
                this.newickStringInput.apply(beastState, indexVariables);

        try {
            NewickImporter importer =
                    new NewickImporter(newick);

            Tree importedTree =
                    importer.importTree(null);

            return new DefaultTreeModel("tree", importedTree);
        } catch (Importer.ImportException e) {
            throw new TileApplicationError(
                    "Invalid Newick string.",
                    "The provided string could not be parsed as a Newick tree."
            );
        } catch (IOException e) {
            throw new TileApplicationError(
                    "Could not read Newick string.",
                    "The provided Newick string could not be read."
            );
        }
    }
}
