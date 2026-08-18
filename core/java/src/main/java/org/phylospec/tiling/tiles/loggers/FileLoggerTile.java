package org.phylospec.tiling.tiles.loggers;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TiledState;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `fileLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.FileLoggerSpec] on the state.
///
/// @param <S> the tiled state the spec is registered on
/// @param <O> the type of the logged parameters
public class FileLoggerTile<S extends TiledState<O, ?>, O> extends TemplateTile<Void, S> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger fileLogger = fileLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        parameters=$$parameters
                    )
                }""";
    }

    public TemplateTileInput<Integer, S> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<String, S> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<List<O>, S> parametersInput =
            new TemplateTileInput<>("$$parameters", false);

    @Override
    protected Void applyTile(S state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        String fileName = this.fileNameInput.apply(state, indexVariables);
        List<O> parameters = this.parametersInput.apply(state, indexVariables);

        state.addFileLoggerSpec(new FileLoggerSpec<>(logEvery, fileName, parameters));

        return null;
    }
}
