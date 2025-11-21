package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.typeresolver.TypeResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * This interface can be implemented by classes that transform the AST tree.
 * The transformation should be stateless to be correct.
 */
public abstract class AstTransformer implements AstVisitor<Stmt, Expr, AstType> {
    List<Stmt> oldStatements;
    List<Stmt> transformedStatements;

    public List<Stmt> transformStatements(List<Stmt> statements) {
        oldStatements = statements;
        transformedStatements = new ArrayList<>();

        for (Stmt oldStatement : statements) {
            oldStatement.accept(this);
        }

        return transformedStatements;
    }

    @Override
    public Stmt visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.statement = stmt.statement.accept(this);

        boolean isOldStatement = oldStatements.contains(stmt);
        if (isOldStatement) {
            transformedStatements.add(stmt);
        }

        return stmt;
    }

    @Override
    public Stmt visitAssignment(Stmt.Assignment stmt) {
        stmt.expression = stmt.expression.accept(this);

        boolean isOldStatement = oldStatements.contains(stmt);
        if (isOldStatement) {
            transformedStatements.add(stmt);
        }

        return stmt;
    }

    @Override
    public Stmt visitDraw(Stmt.Draw stmt) {
        stmt.expression = stmt.expression.accept(this);

        boolean isOldStatement = oldStatements.contains(stmt);
        if (isOldStatement) {
            transformedStatements.add(stmt);
        }

        return stmt;
    }

    @Override
    public Stmt visitImport(Stmt.Import stmt) {
        return stmt;
    }

    @Override
    public Expr visitLiteral(Expr.Literal expr) {
        return expr;
    }

    @Override
    public Expr visitVariable(Expr.Variable expr) {
        return expr;
    }

    @Override
    public Expr visitUnary(Expr.Unary expr) {
        expr.right = expr.right.accept(this);
        return expr;
    }

    @Override
    public Expr visitBinary(Expr.Binary expr) {
        expr.left = expr.left.accept(this);
        expr.right = expr.right.accept(this);
        return expr;
    }

    @Override
    public Expr visitCall(Expr.Call expr) {
        for (int i = 0; i < expr.arguments.length; i++) {
            expr.arguments[i] = (Expr.Argument) expr.arguments[i].accept(this);
        }
        return expr;
    }

    @Override
    public Expr visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression = expr.expression.accept(this);
        return expr;
    }

    @Override
    public Expr visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression = expr.expression.accept(this);
        return expr;
    }

    @Override
    public Expr visitGrouping(Expr.Grouping expr) {
        expr.expression = expr.expression.accept(this);
        return expr;
    }

    @Override
    public Expr visitArray(Expr.Array expr) {
        expr.elements.replaceAll(expr1 -> expr1.accept(this));
        return expr;
    }

    @Override
    public Expr visitListComprehension(Expr.ListComprehension expr) {
        expr.expression = expr.expression.accept(this);
        expr.list = expr.list.accept(this);
        return expr;
    }

    @Override
    public Expr visitGet(Expr.Get expr) {
        expr.object = expr.object.accept(this);
        return expr;
    }

    @Override
    public AstType visitAtomicType(AstType.Atomic expr) {
        return expr;
    }

    @Override
    public AstType visitGenericType(AstType.Generic expr) {
        for (int i = 0; i < expr.typeParameters.length; i++) {
            expr.typeParameters[i] = expr.typeParameters[i].accept(this);
        }
        return expr;
    }
}
