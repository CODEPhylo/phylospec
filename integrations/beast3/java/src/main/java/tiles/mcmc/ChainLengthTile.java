package tiles.mcmc;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.tiling.tiles.TemplateTile;
import beastconfig.BEASTState;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ChainLengthTile extends TemplateTile<Void, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Any chainLength = $chainLength
                }""";
    }

    public TemplateTileInput<Integer, BEASTState> chainLengthInput = new TemplateTileInput<>(
            "$chainLength", Set.of(Stochasticity.CONSTANT)
    );

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        // make sure that the variable name is actually chainLength, as the template matcher usually ignores variable names

        if (!(node instanceof Stmt.Assignment assignment)) throw new FailedTilingAttempt.Irrelevant();
        if (!assignment.name.equals("chainLength")) throw new FailedTilingAttempt.Irrelevant();

        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        beastState.chainLength = this.chainLengthInput.apply(beastState, indexVariables);
        return null;
    }

}
