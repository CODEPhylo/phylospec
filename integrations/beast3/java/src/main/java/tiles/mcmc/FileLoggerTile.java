package tiles.mcmc;

import beast.base.core.BEASTObject;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `fileLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.FileLoggerSpec] on the state.
public class FileLoggerTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger fileLogger = fileLogger(
                        file=$fileName,
                        logEvery=$$logEvery,
                        parameters=$$parameters,
                        parameterNames=$$parameterNames
                    )
                }""";
    }

    public TemplateTileInput<String, BEASTState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<Integer, BEASTState> logEveryInput =
            new TemplateTileInput<>("$$logEvery", false, Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<List<? extends BEASTObject>, BEASTState> parametersInput =
            new TemplateTileInput<>("$$parameters", false);
    public TemplateTileInput<List<String>, BEASTState> parameterNamesInput =
            new TemplateTileInput<>("$$parameterNames", false, Set.of(Stochasticity.CONSTANT));

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String fileName = state.resolveUserOutputPath(this.fileNameInput.apply(state, indexVariables));
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        List<? extends BEASTObject> parameters = this.parametersInput.apply(state, indexVariables);
        List<String> parameterNames = this.parameterNamesInput.apply(state, indexVariables);

        List<BEASTObject> generalParameters = parameters != null ? new ArrayList<>(parameters) : null;
        state.addFileLoggerSpec(new FileLoggerSpec<>(
                logEvery != null ? logEvery : LoggerSpec.DEFAULT_LOG_EVERY,
                fileName,
                generalParameters,
                parameterNames));

        return null;
    }
}
