package org.phylospec.typeresolver;

import org.phylospec.ast.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the stochasticity of each node of the AST tree: for each node, it is determined if it
 * represents a deterministic or stochastic expression.
 */
public class StochasticityResolver implements AstVisitor<Stochasticity, Stochasticity, Stochasticity> {

    private final Map<AstNode, Stochasticity> stochasticityMap;
    private final Map<String, Stochasticity> variableStochasticityMap;

    public StochasticityResolver() {
        this.stochasticityMap = new HashMap<>();
        this.variableStochasticityMap = new HashMap<>();
    }

    public Stochasticity getStochasticity(AstNode node) {
        return stochasticityMap.get(node);
    }

    @Override
    public Stochasticity visitDecoratedStmt(Stmt.Decorated stmt) {
        return remember(stmt, stmt.statememt.accept(this));
    }

    @Override
    public Stochasticity visitAssignment(Stmt.Assignment stmt) {
        Stochasticity expressionStochasticity = stmt.expression.accept(this);
        remember(stmt.name, expressionStochasticity);
        return remember(stmt, expressionStochasticity);
    }

    @Override
    public Stochasticity visitDraw(Stmt.Draw stmt) {
        remember(stmt.name, Stochasticity.STOCHASTIC);
        return remember(stmt, Stochasticity.STOCHASTIC);
    }

    @Override
    public Stochasticity visitImport(Stmt.Import stmt) {
        return remember(stmt, Stochasticity.UNDEFINED);
    }

    @Override
    public Stochasticity visitLiteral(Expr.Literal expr) {
        return remember(expr, Stochasticity.DETERMINISTIC);
    }

    @Override
    public Stochasticity visitVariable(Expr.Variable expr) {
        return remember(expr, variableStochasticityMap.getOrDefault(expr.variableName, Stochasticity.DETERMINISTIC));
    }

    @Override
    public Stochasticity visitUnary(Expr.Unary expr) {
        return remember(expr, expr.right.accept(this));
    }

    @Override
    public Stochasticity visitBinary(Expr.Binary expr) {
        Stochasticity leftStochasticity = expr.left.accept(this);
        Stochasticity rightStochasticity = expr.right.accept(this);
        return remember(expr, Stochasticity.merge(leftStochasticity, rightStochasticity));
    }

    @Override
    public Stochasticity visitCall(Expr.Call expr) {
        return remember(
                expr,
                Stochasticity.merge(
                        Arrays.stream(expr.arguments).map(x -> x.accept(this)).toList()
                )
        );
    }

    @Override
    public Stochasticity visitAssignedArgument(Expr.AssignedArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Stochasticity visitDrawnArgument(Expr.DrawnArgument expr) {
        return remember(expr, Stochasticity.STOCHASTIC);
    }

    @Override
    public Stochasticity visitGrouping(Expr.Grouping expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Stochasticity visitArray(Expr.Array expr) {
        return remember(
                expr,
                Stochasticity.merge(
                        expr.elements.stream().map(x -> x.accept(this)).toList()
                )
        );
    }

    @Override
    public Stochasticity visitGet(Expr.Get expr) {
        return remember(expr, expr.object.accept(this));
    }

    @Override
    public Stochasticity visitAtomicType(AstType.Atomic expr) {
        return remember(expr, Stochasticity.UNDEFINED);
    }

    @Override
    public Stochasticity visitGenericType(AstType.Generic expr) {
        return remember(expr, Stochasticity.UNDEFINED);
    }

    private Stochasticity remember(AstNode astNode, Stochasticity stochasticity) {
        this.stochasticityMap.put(astNode, stochasticity);
        return stochasticity;
    }

    private Stochasticity remember(String variableName, Stochasticity stochasticity) {
        this.variableStochasticityMap.put(variableName, stochasticity);
        return stochasticity;
    }
}
