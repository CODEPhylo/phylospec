package tiles;

import beast.base.spec.domain.*;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeResolver;
import patternmatching.EvaluatedTile;
import patternmatching.ExprNodeTile;
import patternmatching.TypeToken;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LiteralTile extends ExprNodeTile<Expr.Literal> {
    @Override
    public Class<Expr.Literal> getTargetNodeType() {
        return Expr.Literal.class;
    }

    @Override
    public Set<EvaluatedTile> tryToTileExpr(Expr.Literal expr, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver) {
        Set<ResolvedType> resolvedTypeSet = typeResolver.resolveTypeSet(expr);
        Set<EvaluatedTile> evaluatedTiles = new HashSet<>();



        for (ResolvedType resolvedType : resolvedTypeSet) {
            switch (resolvedType.getName()) {
                case "phylospec.types.Real" -> {
                    RealScalarParam<Real> state = new RealScalarParam<>(0.0, Real.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<RealScalarParam<Real>>() {}.getType(), 1
                            )
                    );
                }
                case "phylospec.types.NonNegativeReal" -> {
                    RealScalarParam<NonNegativeReal> state = new RealScalarParam<>(0.5, NonNegativeReal.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<RealScalarParam<NonNegativeReal>>() {}.getType(), 1
                            )
                    );
                }
                case "phylospec.types.PositiveReal" -> {
                    RealScalarParam<PositiveReal> state = new RealScalarParam<>(0.5, PositiveReal.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<RealScalarParam<PositiveReal>>() {}.getType(), 1
                            )
                    );
                }
                case "phylospec.types.Integer" -> {
                    IntScalarParam<Int> state = new IntScalarParam<>(0, Int.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<IntScalarParam<Int>>() {}.getType(), 1
                            )
                    );
                }
                case "phylospec.types.NonNegativeInteger" -> {
                    IntScalarParam<NonNegativeInt> state = new IntScalarParam<>(1, NonNegativeInt.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<IntScalarParam<NonNegativeInt>>() {}.getType(), 1
                            )
                    );
                }
                case "phylospec.types.PositiveInteger" -> {
                    IntScalarParam<PositiveInt> state = new IntScalarParam<>(1, PositiveInt.INSTANCE);
                    evaluatedTiles.add(
                            new EvaluatedTile(
                                    this, state, new TypeToken<IntScalarParam<PositiveInt>>() {}.getType(), 1
                            )
                    );
                }
            }
        }

        return evaluatedTiles;
    }
}
