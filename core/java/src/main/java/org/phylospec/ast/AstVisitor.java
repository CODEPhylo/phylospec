package org.phylospec.ast;

import java.util.Arrays;
import java.util.List;

/**
 * This interface can be implemented by classes that do some operation
 * on the AST tree (e.g. pretty-printing or syntax checking). It follows
 * the visitor pattern.
 */
public interface AstVisitor<S, E, T> {
    default S visitStatements(List<Stmt> statements) {
        S last = null;
        for (Stmt stmt : statements) {
            last = stmt.accept(this);
        }
        return last;
    }

    default S visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.statement.accept(this);
        return null;
    }

    default S visitAssignment(Stmt.Assignment stmt) {
        stmt.type.accept(this);
        stmt.expression.accept(this);
        return null;
    }

    default S visitDraw(Stmt.Draw stmt) {
        stmt.type.accept(this);
        stmt.expression.accept(this);
        return null;
    }

    default S visitImport(Stmt.Import stmt) {
        return null;
    }

    default S visitIndexedStmt(Stmt.Indexed indexed) {
        indexed.statement.accept(this);
        indexed.ranges.stream().forEach(x -> x.accept(this));
        return null;
    }

    default S visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        observedAs.stmt.accept(this);
        observedAs.observedAs.accept(this);
        return null;
    }

    default S visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        observedBetween.stmt.accept(this);
        observedBetween.observedFrom.accept(this);
        observedBetween.observedTo.accept(this);
        return null;
    }

    default E visitLiteral(Expr.Literal expr) {
        return null;
    }

    default E visitStringTemplate(Expr.StringTemplate expr) {
        for (Expr.StringTemplate.Part part : expr.parts) {
            if (part instanceof Expr.StringTemplate.ExpressionPart expressionPart) expressionPart.expression().accept(this);
        }

        return null;
    }

    default E visitVariable(Expr.Variable expr) {
        return null;
    }

    default E visitTemplateVariable(Expr.TemplateVariable expr) {
        return null;
    }

    default E visitOptionalTemplateVariable(Expr.OptionalTemplateVariable expr) {
        return null;
    }

    default E visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return null;
    }

    default E visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return null;
    }

    default E visitCall(Expr.Call expr) {
        Arrays.stream(expr.arguments).forEach(x -> x.accept(this));
        return null;
    }

    default E visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return null;
    }

    default E visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
        return null;
    }

    default E visitGrouping(Expr.Grouping expr) {
        expr.expression.accept(this);
        return null;
    }

    default E visitArray(Expr.Array expr) {
        expr.elements.forEach(x -> x.accept(this));
        return null;
    }

    default E visitIndex(Expr.Index expr) {
        expr.object.accept(this);
        expr.indices.forEach(x -> x.accept(this));
        return null;
    }

    default E visitRange(Expr.Range range) {
        range.from.accept(this);
        range.to.accept(this);
        return null;
    }

    default T visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    default T visitGenericType(AstType.Generic expr) {
        Arrays.stream(expr.typeParameters).forEach(x -> x.accept(this));
        return null;
    }
}
