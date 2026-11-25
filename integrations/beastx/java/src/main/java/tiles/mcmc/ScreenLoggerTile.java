package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/// Matches a `screenLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.ScreenLoggerSpec] on the state.
public class ScreenLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger screenLogger = screenLogger(
                        logEvery=$$logEvery,
                        parameters=$$parameters,
                        parameterNames=$$parameterNames
                    )
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$$logEvery", false, Set.of(Stochasticity.CONSTANT));
    public TileInput<List<String>, BeastXState> parametersInput = new LoggerParameterNamesInput("$$parameters");
    public TemplateTileInput<List<String>, BeastXState> parameterNamesInput =
            new TemplateTileInput<>("$$parameterNames", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        List<String> parameters = this.parametersInput.apply(beastState, indexVariables);
        List<String> parameterNames = this.parameterNamesInput.apply(beastState, indexVariables);

        beastState.addScreenLoggerSpec(new ScreenLoggerSpec<>(
                logEvery != null ? logEvery : LoggerSpec.DEFAULT_LOG_EVERY,
                parameters,
                parameterNames));

        return null;
    }
}
