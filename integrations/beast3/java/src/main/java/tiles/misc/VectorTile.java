package tiles.misc;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.AstNodeTile;
import tiling.BEASTState;
import tiling.FailedTilingAttempt;
import tiling.Tile;
import tiling.TypeToken;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VectorTile<T> extends AstNodeTile<T, Expr.Literal> {
    private final TypeToken<T> typeToken;
    private final T value;

    public VectorTile() {
        this(new TypeToken<>() {
        }, null);
    }

    public VectorTile(TypeToken<T> typeToken, T value) {
        this.typeToken = typeToken;
        this.value = value;
    }

    @Override
    public Class<Expr.Literal> getTargetNodeType() {
        return Expr.Literal.class;
    }

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Literal literal)) throw new FailedTilingAttempt.Irrelevant();

        // depending on the actual literal, we return different tiles

        if (literal.value instanceof String string) {
            return Set.of(new VectorTile<String>(new TypeToken<String>() {
            }, string));
        }

        if (literal.value instanceof Integer number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, number));
            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, number.doubleValue()));

            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, new IntScalarParam<>(number, Int.INSTANCE)));
            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number.doubleValue(), Real.INSTANCE)));

            if (0 <= number) {
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, NonNegativeInt.INSTANCE)));
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), NonNegativeReal.INSTANCE)));
            }

            if (0 < number) {
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new IntScalarParam<>(number, PositiveInt.INSTANCE)));
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number.doubleValue(), PositiveReal.INSTANCE)));
            }

            return tiles;
        }

        if (literal.value instanceof Double number) {
            Set<Tile<?>> tiles = new HashSet<>();

            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, number));

            tiles.add(new VectorTile<>(new TypeToken<>() {
            }, new RealScalarParam<>(number, Real.INSTANCE)));

            if (0 <= number) {
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, NonNegativeReal.INSTANCE)));
            }

            if (0 < number) {
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, PositiveReal.INSTANCE)));
            }

            if (0 < number && number < 1) {
                tiles.add(new VectorTile<>(new TypeToken<>() {
                }, new RealScalarParam<>(number, UnitInterval.INSTANCE)));
            }

            return tiles;
        }

        throw new FailedTilingAttempt.Irrelevant();
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
        return new VectorTile<>(new TypeToken<>() {
        }, null);
    }
}
