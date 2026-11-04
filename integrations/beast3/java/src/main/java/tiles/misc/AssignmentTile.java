package tiles.misc;

import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment, BEASTState> {

    AstNodeTileInput<Object, Stmt.Assignment, BEASTState> expressionInput =
            new AstNodeTileInput<>("expression", expr -> expr.expression);

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> candidates =
                super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
        Set<String> declaredTypes = ((Stmt.Assignment) node).type.getResolvedTypeNames();
        boolean integer = declaredTypes.contains("phylospec.types.Integer");
        boolean real = declaredTypes.contains("phylospec.types.Real");

        // Numeric literals can have both native scalar representations. Preserve the declaration's
        // family even when a later consumer (such as a logger) accepts either representation.
        // Other representations and declarations without a unique scalar family stay unchanged.
        if (integer != real) {
            TypeToken<?> incompatible = integer ? TypeToken.of(RealScalar.class) : TypeToken.of(IntScalar.class);
            candidates.removeIf(candidate -> incompatible.isAssignableFrom(candidate.getTypeToken()));
            if (candidates.isEmpty()) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "BEAST cannot bind this result to the declared numeric type '"
                                + ((Stmt.Assignment) node).type.name
                                + "'.");
            }
        }
        return candidates;
    }

    @Override
    public Object applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }
}
