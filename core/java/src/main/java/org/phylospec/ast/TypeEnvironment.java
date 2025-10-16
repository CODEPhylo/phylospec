package org.phylospec.ast;

import java.util.Map;

public class TypeEnvironment {
    Map<Expr, TypedExpr> typedExpressions;

    private record TypedExpr(Expr expression, AstType type) {}
}
