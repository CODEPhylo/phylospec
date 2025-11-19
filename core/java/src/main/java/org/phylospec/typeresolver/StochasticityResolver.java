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
    private final Map<AstNode, Stochasticity> fixedStochasticities;

    public StochasticityResolver() {
        this.stochasticityMap = new HashMap<>();
        this.variableStochasticityMap = new HashMap<>();
        this.fixedStochasticities = new HashMap<>();
    }

    /** Returns the stochasticity of the expression corresponding to the given AST node. */
    public Stochasticity getStochasticity(AstNode node) {
        return fixedStochasticities.getOrDefault(node, stochasticityMap.get(node));
    }

    /** Returns the stochasticity of the expression corresponding to the given AST node. */
    public void fixStochasticity(AstNode node, Stochasticity stochasticity) {
        fixedStochasticities.put(node, stochasticity);
    }

    @Override
    public Stochasticity visitDecoratedStmt(Stmt.Decorated stmt) {
        return remember(stmt, stmt.statement.accept(this));
    }

    @Override
    public Stochasticity visitAssignment(Stmt.Assignment stmt) {
        Stochasticity expressionStochasticity = stmt.expression.accept(this);
        remember(stmt.name, expressionStochasticity);
        return remember(stmt, expressionStochasticity);
    }

    @Override
    public Stochasticity visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);
        remember(stmt.name, Stochasticity.STOCHASTIC);
        return remember(stmt, Stochasticity.STOCHASTIC);
    }

    @Override
    public Stochasticity visitImport(Stmt.Import stmt) {
        return remember(stmt, Stochasticity.UNDEFINED);
    }

    @Override
    public Stochasticity visitLiteral(Expr.Literal expr) {
        return remember(expr, Stochasticity.CONSTANT);
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
        return remember(expr, Stochasticity.nonConstant(
                Stochasticity.merge(leftStochasticity, rightStochasticity)
        ));
    }

    @Override
    public Stochasticity visitCall(Expr.Call expr) {
        return remember(
                expr,
                Stochasticity.nonConstant(
                        Stochasticity.merge(
                                Arrays.stream(expr.arguments).map(x -> x.accept(this)).toList()
                        )
                )
        );
    }

    @Override
    public Stochasticity visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Stochasticity visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
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
    public Stochasticity visitListComprehension(Expr.ListComprehension expr) {
        return remember(
                expr,
                Stochasticity.merge(
                        expr.expression.accept(this),
                        expr.list.accept(this)
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
