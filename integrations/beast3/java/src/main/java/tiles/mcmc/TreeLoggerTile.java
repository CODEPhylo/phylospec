package tiles.mcmc;

import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `treeLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.TreeLoggerSpec] on the state.
public class TreeLoggerTile extends TemplateTile<Void, BEASTState> {

    // the logger is configured by the engine and is not a generator of the component library
    @Override
    public List<Generator__1> getGeneratorPatterns() {
        return List.of();
    }

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        tree=$$tree
                    )
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<String, BEASTState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$$tree", false);

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        String fileName = state.resolveUserOutputPath(this.fileNameInput.apply(state, indexVariables));
        Tree tree = this.treeInput.apply(state, indexVariables);

        state.addTreeLoggerSpec(new TreeLoggerSpec<>(logEvery, fileName, tree));

        return null;
    }
}
