package org.phylospec.ast;

/**
 * This interface can be implemented by classes that do some operation
 * on the AST tree (e.g. pretty-printing or syntax checking). It follows
 * the visitor pattern.
 */
public interface AstVisitor<T> {
    public T visitDecoratedStmt(Stmt.Decorated stmt);
    public T visitAssignment(Stmt.Assignment stmt);
    public T visitDraw(Stmt.Draw stmt);

    public T visitLiteral(Expr.Literal expr);
    public T visitVariable(Expr.Variable expr);
    public T visitUnary(Expr.Unary expr);
    public T visitBinary(Expr.Binary expr);
    public T visitCall(Expr.Call expr);
    public T visitAssignedArgument(Expr.AssignedArgument expr);
    public T visitDrawnArgument(Expr.DrawnArgument expr);
    public T visitGrouping(Expr.Grouping expr);
    public T visitArray(Expr.Array expr);
    public T visitGet(Expr.Get expr);

    public T visitAtomicType(Type.Atomic expr);
    public T visitGenericType(Type.Generic expr);
}
