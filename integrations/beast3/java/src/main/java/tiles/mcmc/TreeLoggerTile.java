package tiles.mcmc;

import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `treeLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.TreeLoggerSpec] on the state.
public class TreeLoggerTile extends TemplateTile<Void, BEASTState> {

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

    public TemplateTileInput<String, BEASTState> fileNameInput =
            new TemplateTileInput<>("$$fileName", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    public TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$tree");
    public TemplateTileInput<Integer, BEASTState> logEveryInput =
            new TemplateTileInput<>("$$logEvery", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String fileName = this.fileNameInput.apply(state, indexVariables);
        fileName = state.resolveOutputPath(fileName != null ? fileName : OutputPaths.getTreeFile(state.outputPrefix));
        Tree tree = this.treeInput.apply(state, indexVariables);
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);

        state.addTreeLoggerSpec(
                new TreeLoggerSpec<>(logEvery != null ? logEvery : LoggerSpec.DEFAULT_LOG_EVERY, fileName, tree));

        return null;
    }
}
