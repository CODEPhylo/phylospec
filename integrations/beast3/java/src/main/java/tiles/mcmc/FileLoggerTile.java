package tiles.mcmc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import beast.base.core.BEASTObject;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
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
                        logEvery=$logEvery,
                        file=$fileName,
                        parameters=$$parameters
                    )
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<String, BEASTState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<List<? extends BEASTObject>, BEASTState> parametersInput =
            new TemplateTileInput<>("$$parameters", false);

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        String fileName = this.fileNameInput.apply(state, indexVariables);
        List<? extends BEASTObject> parameters = this.parametersInput.apply(state, indexVariables);

        List<BEASTObject> generalParameters = new ArrayList<>(parameters);
        state.addFileLoggerSpec(new FileLoggerSpec<>(logEvery, fileName, generalParameters));

        return null;
    }
}
