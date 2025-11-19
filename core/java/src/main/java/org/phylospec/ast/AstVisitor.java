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

    public E visitLiteral(Expr.Literal expr);
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

    public T visitAtomicType(AstType.Atomic expr);
    public T visitGenericType(AstType.Generic expr);
}
