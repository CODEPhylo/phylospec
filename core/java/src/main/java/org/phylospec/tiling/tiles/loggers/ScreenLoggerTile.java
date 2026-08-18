package org.phylospec.tiling.tiles.loggers;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TiledState;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `screenLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.ScreenLoggerSpec] on the state.
///
/// @param <S> the tiled state the spec is registered on
/// @param <O> the type of the logged parameters
public class ScreenLoggerTile<S extends TiledState<O, ?>, O> extends TemplateTile<Void, S> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger screenLogger = screenLogger(
                        logEvery=$logEvery,
                        parameters=$$parameters
                    )
                }""";
    }

    public TemplateTileInput<Integer, S> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<List<O>, S> parametersInput =
            new TemplateTileInput<>("$$parameters", false);

    @Override
    protected Void applyTile(S state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        List<O> parameters = this.parametersInput.apply(state, indexVariables);

        state.addScreenLoggerSpec(new ScreenLoggerSpec<>(logEvery, parameters));

        return null;
    }
}
