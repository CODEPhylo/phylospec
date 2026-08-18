package org.phylospec.tiling.tiles.loggers;

import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TiledState;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `treeLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.TreeLoggerSpec] on the state.
///
/// @param <S> the tiled state the spec is registered on
/// @param <T> the type of the logged tree
public class TreeLoggerTile<S extends TiledState<?, T>, T> extends TemplateTile<Void, S> {

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

    public TemplateTileInput<Integer, S> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<String, S> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<T, S> treeInput = new TemplateTileInput<>("$$tree", false);

    @Override
    protected Void applyTile(S state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        String fileName = this.fileNameInput.apply(state, indexVariables);
        T tree = this.treeInput.apply(state, indexVariables);

        state.addTreeLoggerSpec(new TreeLoggerSpec<>(logEvery, fileName, tree));

        return null;
    }
}
