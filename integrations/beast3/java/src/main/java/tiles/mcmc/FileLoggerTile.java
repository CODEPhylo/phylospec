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

public class FileLoggerTile extends TemplateTile<Void> {

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

    public TemplateTileInput<Integer> logEveryInput = new TemplateTileInput<>(
            "$logEvery", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<String> fileNameInput = new TemplateTileInput<>(
            "$fileName", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<List<BEASTObject>> parametersInput = new TemplateTileInput<>(
            "$$parameters", false
    );

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        String fileName = this.fileNameInput.apply(beastState, indexVariables);
        List<BEASTObject> parameters = this.parametersInput.apply(beastState, indexVariables);

        if (parameters == null) {
            parameters = LoggerSelector.getLoggableObjects(beastState);
        }

        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, logEvery);
        beastState.setInput(logger, logger.fileNameInput, fileName);
        beastState.setInput(logger, logger.loggersInput, parameters);
        beastState.setInput(logger, logger.sanitiseHeadersInput, true);
        beastState.setInput(logger, logger.sortModeInput, Logger.SORTMODE.smart);
        beastState.addFileLogger(logger);

        return null;
    }

}
