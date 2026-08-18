package tiles.mcmc;

import beast.base.core.BEASTObject;
import beastconfig.LoggerSelector;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.tiling.tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

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

    public TemplateTileInput<Integer, BEASTState> logEveryInput = new TemplateTileInput<>(
            "$logEvery", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<String, BEASTState> fileNameInput = new TemplateTileInput<>(
            "$fileName", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<List<BEASTObject>, BEASTState> parametersInput = new TemplateTileInput<>(
            "$$parameters", false
    );

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        String fileName = this.fileNameInput.apply(beastState, indexVariables);
        List<BEASTObject> parameters = this.parametersInput.apply(beastState, indexVariables);

        beastState.addFileLoggerSpec(new FileLoggerSpec<>(logEvery, fileName, parameters, new HashMap<>()));

        return null;
    }

}
