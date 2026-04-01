package tiles;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.TypeResolver;
import patternmatching.EvaluatedTile;
import patternmatching.AstNodeTile;
import patternmatching.TypeToken;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LiteralTile extends AstNodeTile<Expr.Literal> {
    @Override
    public Class<Expr.Literal> getTargetNodeType() {
        return Expr.Literal.class;
    }

    @Override
    public Set<EvaluatedTile> applyTile(Expr.Literal expr) {
        return switch (expr.value) {
            case String value -> Set.of(
                    new EvaluatedTile(this, value, new TypeToken<String>() {}.getType())
            );
            case Integer value -> {
                Set<EvaluatedTile> tiles = new HashSet<>();

                tiles.add(
                        new EvaluatedTile(this, new IntScalarParam<>(1, Int.INSTANCE), new TypeToken<IntScalarParam<Int>>() {}.getType())
                );

                if (0 <= value)
                    tiles.add(
                            new EvaluatedTile(this, new IntScalarParam<>(1, NonNegativeInt.INSTANCE), new TypeToken<IntScalarParam<NonNegativeInt>>() {}.getType())
                    );

                if (0 < value)
                    tiles.add(
                            new EvaluatedTile(this, new IntScalarParam<>(1, PositiveInt.INSTANCE), new TypeToken<IntScalarParam<PositiveInt>>() {}.getType())
                    );

                yield tiles;
            }
            case Double value -> {
                Set<EvaluatedTile> tiles = new HashSet<>();

                tiles.add(
                        new EvaluatedTile(this, new RealScalarParam<>(1.0, Real.INSTANCE), new TypeToken<RealScalarParam<Real>>() {}.getType())
                );

                if (0 <= value)
                    tiles.add(
                            new EvaluatedTile(this, new RealScalarParam<>(1.0, NonNegativeReal.INSTANCE), new TypeToken<RealScalarParam<NonNegativeReal>>() {}.getType())
                    );

                if (0 < value)
                    tiles.add(
                            new EvaluatedTile(this, new RealScalarParam<>(1.0, PositiveReal.INSTANCE), new TypeToken<RealScalarParam<PositiveReal>>() {}.getType())
                    );

                if (0 < value && value < 1)
                    tiles.add(
                            new EvaluatedTile(this, new RealScalarParam<>(0.5, UnitInterval.INSTANCE), new TypeToken<RealScalarParam<UnitInterval>>() {}.getType())
                    );

                yield tiles;
            }
            default -> Set.of();
        };
    }
}
