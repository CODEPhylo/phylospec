package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class TreeLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        trees=$$trees
                    )
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));

    public TemplateTileInput<String, BeastXState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));

    public TileInput<LoggerTreeNames, BeastXState> treesInput =
            new LoggerTreeNamesInput("$$trees", false);

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int logEvery =
                this.logEveryInput.apply(beastState, indexVariables);

        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Tree logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        String fileName =
                this.fileNameInput.apply(beastState, indexVariables);

        LoggerTreeNames trees =
                this.treesInput.apply(beastState, indexVariables);

        beastState.addTreeLoggerSpec(
                logEvery,
                fileName,
                trees == null ? null : trees.names
        );

        return null;
    }
}
