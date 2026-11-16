package tiles.mcmc;

import beast.base.core.BEASTObject;
import beastconfig.BEASTState;
import java.util.*;
import org.phylospec.ast.Expr;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.typeresolver.Stochasticity;

/// Matches a `screenLogger(...)` declaration in the `mcmc` block and registers a
/// [org.phylospec.tiling.mcmc.ScreenLoggerSpec] on the state.
public class ScreenLoggerTile extends TemplateTile<Void, BEASTState> {

    // the logger is configured by the engine and is not a generator of the component library
    @Override
    public List<Generator__1> getGeneratorPatterns() {
        return List.of();
    }

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

    public TemplateTileInput<Integer, BEASTState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));
    public TemplateTileInput<List<? extends BEASTObject>, BEASTState> parametersInput =
            new TemplateTileInput<>("$$parameters", false);

    @Override
    protected Void applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(state, indexVariables);
        List<? extends BEASTObject> parameters = this.parametersInput.apply(state, indexVariables);

        List<BEASTObject> generalParameters = parameters != null ? new ArrayList<>(parameters) : null;
        state.addScreenLoggerSpec(new ScreenLoggerSpec<>(logEvery, generalParameters));

        return null;
    }
}
