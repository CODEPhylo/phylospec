package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class ScreenLoggerTile extends TemplateTile<Void, BeastXState> {

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

    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));

    public TileInput<LoggerParameterNames, BeastXState> parametersInput =
            new LoggerParameterNamesInput("$$parameters", false);

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int logEvery =
                this.logEveryInput.apply(beastState, indexVariables);

        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Screen logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        LoggerParameterNames parameters =
                this.parametersInput.apply(beastState, indexVariables);

        beastState.addScreenLoggerSpec(
                logEvery,
                parameters == null ? null : parameters.names
        );

        return null;
    }
}
