package tiles.functions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastobjects.ScaledScalar;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/**
 * Tiles {@code c * x}, a positive constant times a scalar, into a {@link ScaledScalar}.
 *
 * <p>The arithmetic tiles build an RPN calculator, which BEAST types as a tensor and which no
 * input asking for a {@code RealScalar} accepts, so arithmetic has only worked on the right of
 * an assignment. This tile covers the one product a partition's relative rate needs,
 * {@code rate=(N / n_i) * w[i]}, wherever a positive scalar is wanted.
 */
public class ScaleTile extends AstNodeTile<RealScalar<PositiveReal>, Expr.Binary, BEASTState> {

    AstNodeTileInput<Double, Expr.Binary, BEASTState> factorInput = new AstNodeTileInput<>("factor", expr -> expr.left);
    AstNodeTileInput<RealScalar<?>, Expr.Binary, BEASTState> scalarInput =
            new AstNodeTileInput<>("scalar", expr -> expr.right);

    @Override
    public Class<Expr.Binary> getTargetNodeType() {
        return Expr.Binary.class;
    }

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary) || binary.operator != TokenType.STAR) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RealScalar<PositiveReal> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double factor = this.factorInput.apply(beastState, indexVariables);
        RealScalar scalar = this.scalarInput.apply(beastState, indexVariables);

        if (!(factor > 0)) {
            throw new TileApplicationError(
                    "The factor " + factor + " is not greater than zero.",
                    "Use a positive constant, e.g. 3.0 * w[1] for three equal partitions.");
        }

        ScaledScalar scaled = new ScaledScalar();
        beastState.setInput(scaled, scaled.scalarInput, scalar);
        beastState.setInput(scaled, scaled.factorInput, factor);
        return scaled;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<RealScalar<PositiveReal>>() {};
    }
}
