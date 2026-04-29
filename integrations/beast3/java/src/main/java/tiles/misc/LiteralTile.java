package tiles.misc;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.*;
import tiles.AstNodeTile;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class LiteralTile<T> extends AstNodeTile<T, Expr.Literal> {
    private final TypeToken<T> typeToken;
    private final T value;

    public LiteralTile() {
        this(new TypeToken<>() {
        }, null, null);
    }

    public LiteralTile(TypeToken<T> typeToken, T value, Expr.Literal astNode) {
        this.typeToken = typeToken;
        this.value = value;
        this.setRootNode(astNode);
    }

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Literal literal)) throw new FailedTilingAttempt.Irrelevant();

        // depending on the actual literal, we return different tiles

        if (literal.value instanceof String string) {
            return Set.of(new LiteralTile<String>(new TypeToken<String>() {
            }, string, literal));
        }

        if (literal.value instanceof Integer number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number, literal));
            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number.doubleValue(), literal));

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new IntScalarParam<>(number, Int.INSTANCE), literal));
            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number.doubleValue(), Real.INSTANCE), literal));

            if (0 <= number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, NonNegativeInt.INSTANCE), literal));
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), NonNegativeReal.INSTANCE), literal));
            }

            if (0 < number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, PositiveInt.INSTANCE), literal));
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), PositiveReal.INSTANCE), literal));
            }

            return tiles;
        }

        if (literal.value instanceof Double number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number, literal));

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number, Real.INSTANCE), literal));

            if (0 <= number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, NonNegativeReal.INSTANCE), literal));
            }

            if (0 < number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, PositiveReal.INSTANCE), literal));
            }

            if (0 < number && number < 1) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, UnitInterval.INSTANCE), literal));
            }

            return tiles;
        }

        throw new FailedTilingAttempt.Irrelevant();
    }

    @Override
    public T applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.value;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    public Tile<?> createInstance() {
        return new LiteralTile<>(new TypeToken<>() {
        }, null, null);
    }
}
