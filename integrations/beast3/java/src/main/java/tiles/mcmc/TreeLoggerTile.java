package tiles.mcmc;

import beast.base.core.BEASTObject;
import beast.base.inference.Logger;
import org.phylospec.typeresolver.Stochasticity;
import tiles.TemplateTile;
import tiling.BEASTState;

import java.util.List;
import java.util.Set;

public class TreeLoggerTile extends TemplateTile<Void> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        logEvery=$logEvery,
                        fileName=$fileName,
                        trees=$$trees
                    )
                }""";
    }

    public TemplateTileInput<Integer> logEveryInput = new TemplateTileInput<>(
            "$logEvery", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<String> fileNameInput = new TemplateTileInput<>(
            "$fileName", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<List<BEASTObject>> treesInput = new TemplateTileInput<>(
            "$$trees", false
    );

    @Override
    protected Void applyTile(BEASTState beastState) {
        Integer logEvery = this.logEveryInput.apply(beastState);
        String fileName = this.fileNameInput.apply(beastState);
        List<BEASTObject> parameters = this.treesInput.apply(beastState);

        if (parameters == null) {
            parameters = beastState.getLoggableTrees();
        }

        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, logEvery);
        beastState.setInput(logger, logger.modeInput, Logger.LOGMODE.tree);
        beastState.setInput(logger, logger.fileNameInput, fileName);
        beastState.setInput(logger, logger.loggersInput, parameters);
        beastState.addScreenLogger(logger);
        
        return null;
    }

}
