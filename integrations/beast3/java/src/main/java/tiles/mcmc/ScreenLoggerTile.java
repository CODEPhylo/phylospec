package tiles.mcmc;

import beast.base.core.BEASTObject;
import beast.base.inference.Logger;
import beastconfig.LoggerSelector;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class ScreenLoggerTile extends TemplateTile<Void> {

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

    public TemplateTileInput<Integer> logEveryInput = new TemplateTileInput<>(
            "$logEvery", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<List<BEASTObject>> parametersInput = new TemplateTileInput<>(
            "$$parameters", false
    );

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        List<BEASTObject> parameters = this.parametersInput.apply(beastState, indexVariables);

        if (parameters == null) {
            parameters = LoggerSelector.getLoggableObjects(beastState);
        }

        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, logEvery);
        beastState.setInput(logger, logger.loggersInput, parameters);
        beastState.addScreenLogger(logger);
        return null;
    }

}
