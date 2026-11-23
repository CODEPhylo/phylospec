package tiles.mcmc;

import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

/// Matches a `treeLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.TreeLoggerSpec] on the state.
public class TreeLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        file=$$fileName,
                        tree=$tree,
                        logEvery=$$logEvery
                    )
                }""";
    }

    public TemplateTileInput<String, BeastXState> fileNameInput =
            new TemplateTileInput<>("$$fileName", false, Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<TreeModel, BeastXState> treeInput = new TemplateTileInput<>("$tree");
    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$$logEvery", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String fileName = this.fileNameInput.apply(beastState, indexVariables);
        if (fileName == null) fileName = (beastState.outputPrefix != null ? beastState.outputPrefix : beastState.runName) + ".trees";
        TreeModel tree = this.treeInput.apply(beastState, indexVariables);
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);

        String treeName = beastState.getPhyloSpecName(tree);
        if (treeName == null) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The tree logger can only log a tree that is part of the model.",
                    "Pass a tree that has a prior, for example tree=tree with `Tree tree ~ Yule(...)`.");
        }

        beastState.addTreeLoggerSpec(new TreeLoggerSpec<>(
                logEvery != null ? logEvery : LoggerSpec.DEFAULT_LOG_EVERY, fileName, treeName));

        return null;
    }
}
