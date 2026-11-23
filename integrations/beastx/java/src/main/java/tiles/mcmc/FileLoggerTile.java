package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/// Matches a `fileLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.FileLoggerSpec] on the state.
public class FileLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger fileLogger = fileLogger(
                        file=$$fileName,
                        logEvery=$$logEvery,
                        parameters=$$parameters,
                        parameterNames=$$parameterNames
                    )
                }""";
    }

    public TemplateTileInput<String, BeastXState> fileNameInput =
            new TemplateTileInput<>("$$fileName", false, Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$$logEvery", false, Set.of(Stochasticity.CONSTANT));
    public TileInput<LoggerParameterNames, BeastXState> parametersInput =
            new LoggerParameterNamesInput("$$parameters", false);
    public TemplateTileInput<List<String>, BeastXState> parameterNamesInput =
            new TemplateTileInput<>("$$parameterNames", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String fileName = this.fileNameInput.apply(beastState, indexVariables);
        if (fileName == null) fileName = (beastState.outputPrefix != null ? beastState.outputPrefix : beastState.runName) + ".log";
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        LoggerParameterNames parameters = this.parametersInput.apply(beastState, indexVariables);
        List<String> parameterNames = this.parameterNamesInput.apply(beastState, indexVariables);

        beastState.addFileLoggerSpec(new FileLoggerSpec<>(
                logEvery != null ? logEvery : LoggerSpec.DEFAULT_LOG_EVERY,
                fileName,
                parameters != null ? parameters.names : null,
                parameterNames));

        return null;
    }
}
