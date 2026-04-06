package tiles.misc;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.VariableResolver;
import tiling.*;
import tiles.AstNodeTile;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LiteralTile<T> extends AstNodeTile<T, Expr.Literal> {
    private final TypeToken<T> typeToken;
    private final T value;

    public LiteralTile() {
        this(new TypeToken<>() {
        }, null);
    }

    public LiteralTile(TypeToken<T> typeToken, T value) {
        this.typeToken = typeToken;
        this.value = value;
    }

    @Override
    public Class<Expr.Literal> getTargetNodeType() {
        return Expr.Literal.class;
    }

    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver) {
        if (!(node instanceof Expr.Literal literal)) return Set.of();

        // depending on the actual literal, we return different tiles

        if (literal.value instanceof String string) {
            return Set.of(new LiteralTile<String>(new TypeToken<String>() {
            }, string));
        }

        if (literal.value instanceof Integer number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number));
            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number.doubleValue()));

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new IntScalarParam<>(number, Int.INSTANCE)));
            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number.doubleValue(), Real.INSTANCE)));

            if (0 <= number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, NonNegativeInt.INSTANCE)));
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), NonNegativeReal.INSTANCE)));
            }

            if (0 < number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, PositiveInt.INSTANCE)));
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), PositiveReal.INSTANCE)));
            }

            return tiles;
        }

        if (literal.value instanceof Double number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, number));

            tiles.add(new LiteralTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number, Real.INSTANCE)));

            if (0 <= number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, NonNegativeReal.INSTANCE)));
            }

            if (0 < number) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, PositiveReal.INSTANCE)));
            }

            if (0 < number && number < 1) {
                tiles.add(new LiteralTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, UnitInterval.INSTANCE)));
            }

            return tiles;
        }

        return Set.of();
    }

    @Override
    public T applyTile(BEASTState beastState) {
        return this.value;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.typeToken;
    }

    @Override
    protected Tile<?> createInstance() {
        return new LiteralTile<>(new TypeToken<>() {
        }, null);
    }
}
