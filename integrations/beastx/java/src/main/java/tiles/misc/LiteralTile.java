package tiles.misc;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.types.RealScalar;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class LiteralTile<T> extends AstNodeTile<T, Expr.Literal, BeastXState> {

    private final TypeToken<T> typeToken;
    private final T value;

    public LiteralTile() {
        this(new TypeToken<>() {}, null, null);
    }

    public LiteralTile(TypeToken<T> typeToken, T value, Expr.Literal astNode) {
        this.typeToken = typeToken;
        this.value = value;
        this.setRootNode(astNode);
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Literal literal)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (literal.value instanceof String string) {
            return Set.of(new LiteralTile<>(new TypeToken<String>() {}, string, literal));
        }

        if (literal.value instanceof Integer number) {
            Set<Tile<?, BeastXState>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<Integer>() {}, number, literal));
            tiles.add(new LiteralTile<>(new TypeToken<Double>() {}, number.doubleValue(), literal));

            double asDouble = number.doubleValue();
            addRealScalarTiles(tiles, asDouble, literal);

            return tiles;
        }

        if (literal.value instanceof Double number) {
            Set<Tile<?, BeastXState>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<Double>() {}, number, literal));
            addRealScalarTiles(tiles, number, literal);

            return tiles;
        }

        throw new FailedTilingAttempt.Irrelevant();
    }

    private static void addRealScalarTiles(
            Set<Tile<?, BeastXState>> tiles,
            double number,
            Expr.Literal literal
    ) {
        tiles.add(new LiteralTile<>(
                new TypeToken<RealScalar<Real>>() {},
                realScalar(number, Real.INSTANCE),
                literal
        ));

        if (number >= 0.0) {
            tiles.add(new LiteralTile<>(
                    new TypeToken<RealScalar<NonNegativeReal>>() {},
                    realScalar(number, NonNegativeReal.INSTANCE),
                    literal
            ));
        }

        if (number > 0.0) {
            tiles.add(new LiteralTile<>(
                    new TypeToken<RealScalar<PositiveReal>>() {},
                    realScalar(number, PositiveReal.INSTANCE),
                    literal
            ));
        }

        if (number > 0.0 && number < 1.0) {
            tiles.add(new LiteralTile<>(
                    new TypeToken<RealScalar<UnitInterval>>() {},
                    realScalar(number, UnitInterval.INSTANCE),
                    literal
            ));
        }
    }

    private static <D extends Real> RealScalar<D> realScalar(double value, D domain) {
        return new BeastXRealScalarParam<>(value, domain);
    }

    @Override
    public T applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.value;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    public Tile<?, BeastXState> createInstance() {
        return new LiteralTile<>(new TypeToken<>() {}, null, null);
    }
}
