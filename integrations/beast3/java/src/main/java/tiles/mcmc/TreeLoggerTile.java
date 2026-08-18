package tiles.mcmc;

import beast.base.evolution.tree.Tree;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.tiling.tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class TreeLoggerTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        tree=$$tree
                    )
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> logEveryInput = new TemplateTileInput<>(
            "$logEvery", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<String, BEASTState> fileNameInput = new TemplateTileInput<>(
            "$fileName", Set.of(Stochasticity.CONSTANT)
    );
    public TemplateTileInput<Tree, BEASTState> treesInput = new TemplateTileInput<>(
            "$$trees", false
    );

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Integer logEvery = this.logEveryInput.apply(beastState, indexVariables);
        String fileName = this.fileNameInput.apply(beastState, indexVariables);
        Tree tree = this.treesInput.apply(beastState, indexVariables);

        beastState.addTreeLoggerSpec(new TreeLoggerSpec<>(logEvery, fileName, tree));
        
        return null;
    }

}
