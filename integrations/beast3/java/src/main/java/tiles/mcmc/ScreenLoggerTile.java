package tiles.mcmc;

import beast.base.core.BEASTObject;
import beast.base.inference.Logger;
import org.phylospec.typeresolver.Stochasticity;
import tiles.TemplateTile;
import tiling.BEASTState;

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
    protected Void applyTile(BEASTState beastState) {
        Integer logEvery = this.logEveryInput.apply(beastState);
        List<BEASTObject> parameters = this.parametersInput.apply(beastState);

        if (parameters == null) {
            parameters = beastState.getLoggableObjects();
        }

        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, logEvery);
        beastState.setInput(logger, logger.loggersInput, parameters);
        beastState.addScreenLogger(logger);
        return null;
    }

}
