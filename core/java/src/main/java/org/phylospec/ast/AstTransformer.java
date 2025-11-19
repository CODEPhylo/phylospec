package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.typeresolver.TypeResolver;

import java.util.List;

/**
 * This interface can be implemented by classes that transform the AST tree.
 * The transformation should be stateless to be correct.
 */
public abstract class AstTransformer<S, E, T> implements AstVisitor<Stmt, Expr, AstType> {
    List<Stmt> oldStatements;
    List<Stmt> transformedStatements;
    TypeResolver typeResolver;

    public List<Stmt> transformStatements(List<Stmt> statements, ComponentResolver componentResolver) {
        typeResolver = new TypeResolver(componentResolver);
        for (Stmt stmt : statements) {
            stmt.accept(typeResolver);
        }

        oldStatements = statements;

        for (Stmt oldStatement : statements) {
            oldStatement.accept(this);
        }

        return transformedStatements;
    }

    @Override
    public Stmt visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.statement.accept(this);

        boolean isOldStatement = oldStatements.contains(stmt);
        if (isOldStatement) {
            transformedStatements.add(stmt);
        }

        return stmt;
    }

    @Override
    public Stmt visitAssignment(Stmt.Assignment stmt) {
        stmt.expression.accept(this);

        boolean isOldStatement = oldStatements.contains(stmt);
        if (isOldStatement) {
            transformedStatements.add(stmt);
        }

        return stmt;
    }

    @Override
    public Stmt visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);

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
        expr.right.accept(this);
        return expr;
    }

    @Override
    public Expr visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return expr;
    }

    @Override
    public Expr visitCall(Expr.Call expr) {
        for (Expr.Argument argument : expr.arguments) {
            argument.accept(this);
        }
        return expr;
    }

    @Override
    public Expr visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return expr;
    }

    @Override
    public Expr visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
        return expr;
    }

    @Override
    public Expr visitGrouping(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    @Override
    public Expr visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return expr;
    }

    @Override
    public Expr visitListComprehension(Expr.ListComprehension expr) {
        expr.expression.accept(this);
        expr.list.accept(this);
        return expr;
    }

    @Override
    public Expr visitGet(Expr.Get expr) {
        expr.object.accept(this);
        return expr;
    }

    @Override
    public AstType visitAtomicType(AstType.Atomic expr) {
        return expr;
    }

    @Override
    public AstType visitGenericType(AstType.Generic expr) {
        for (AstType type : expr.typeParameters) {
            type.accept(this);
        }
        return expr;
    }
}
