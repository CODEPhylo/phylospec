package org.phylospec.ast;

import java.util.List;

/**
 * This interface can be implemented by classes that do some operation
 * on the AST tree (e.g. pretty-printing or syntax checking). It follows
 * the visitor pattern.
 */
public interface AstVisitor<S, E, T> {
    default void visitStatements(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            stmt.accept(this);
        }
    }

    public S visitDecoratedStmt(Stmt.Decorated stmt);
    public S visitAssignment(Stmt.Assignment stmt);
    public S visitDraw(Stmt.Draw stmt);
    public S visitImport(Stmt.Import stmt);
    default S visitIndexedStmt(Stmt.Indexed indexed) {
        return null;
    }
    default S visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        return null;
    }
    default S visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        return null;
    }

    public E visitLiteral(Expr.Literal expr);
    default E visitStringTemplate(Expr.StringTemplate expr) {
        return null;
    }
    public E visitVariable(Expr.Variable expr);
    public E visitUnary(Expr.Unary expr);
    public E visitBinary(Expr.Binary expr);
    public E visitCall(Expr.Call expr);
    public E visitAssignedArgument(Expr.AssignedArgument expr);
    public E visitDrawnArgument(Expr.DrawnArgument expr);
    public E visitGrouping(Expr.Grouping expr);
    public E visitArray(Expr.Array expr);
    public E visitListComprehension(Expr.ListComprehension expr);
    public E visitGet(Expr.Get expr);
    default E visitIndex(Expr.Index expr) {
        return null;
    }
    default E visitRange(Expr.Range range) {
        return null;
    }

    public T visitAtomicType(AstType.Atomic expr);
    public T visitGenericType(AstType.Generic expr);
}
