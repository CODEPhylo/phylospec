package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.TokenType;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.OffsetValue;

/** Adds a fixed constant to a live scalar without changing the scalar's sampling configuration. */
public abstract class OffsetTile extends AstNodeTile<OffsetValue, Expr.Binary, BEASTState> {

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Binary binary) || binary.operator != TokenType.PLUS) {
            throw new FailedTilingAttempt.Irrelevant();
        }
        return super.tryToTile(node, allInputTiles, variableResolver, stochasticityResolver);
    }

    protected OffsetValue createValue(
            BEASTState beastState,
            RealScalar<? extends Real> x,
            double offset,
            IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        OffsetValue value = new OffsetValue();
        beastState.setInput(value, value.xInput, x);
        beastState.setInput(value, value.offsetInput, offset);
        beastState.addCalculationNode(value, new TypeToken<OffsetValue>() {}, this.getId("offset", indexVariables, ""));
        beastState.initBEASTObject(value);
        return value;
    }

    public static class ConstantOnRight extends OffsetTile {

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BEASTState> xInput =
                new AstNodeTileInput<>("x", expr -> expr.left, Set.of(Stochasticity.STOCHASTIC));
        AstNodeTileInput<Double, Expr.Binary, BEASTState> offsetInput = new AstNodeTileInput<>(
                "offset", expr -> expr.right, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        @Override
        public OffsetValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return createValue(
                    beastState,
                    xInput.apply(beastState, indexVariables),
                    offsetInput.apply(beastState, indexVariables),
                    indexVariables);
        }
    }

    public static class ConstantOnLeft extends OffsetTile {

        AstNodeTileInput<RealScalar<? extends Real>, Expr.Binary, BEASTState> xInput =
                new AstNodeTileInput<>("x", expr -> expr.right, Set.of(Stochasticity.STOCHASTIC));
        AstNodeTileInput<Double, Expr.Binary, BEASTState> offsetInput = new AstNodeTileInput<>(
                "offset", expr -> expr.left, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        @Override
        public OffsetValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return createValue(
                    beastState,
                    xInput.apply(beastState, indexVariables),
                    offsetInput.apply(beastState, indexVariables),
                    indexVariables);
        }
    }
}
