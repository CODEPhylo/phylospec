package org.phylospec.ast.transformers;


import org.phylospec.ast.*;
import org.phylospec.typeresolver.TypeResolver;

import java.util.List;

/**
 * This transformation converts a syntax tree into its canonical form.
 * Every canonical statement nests at most two levels (e.g. a literal or an array of variables).
 */
public class CanonicalTransformation extends AstTransformer {

    private int currentLevel = 0;
    private List<String> variables;
    private TypeResolver typeResolver;

    @Override
    public Stmt visitDecoratedStmt(Stmt.Decorated stmt) {
        return null;
    }

    @Override
    public Stmt visitAssignment(Stmt.Assignment stmt) {
        return null;
    }

    @Override
    public Stmt visitDraw(Stmt.Draw stmt) {
        return null;
    }

    @Override
    public Stmt visitImport(Stmt.Import stmt) {
        return null;
    }

    @Override
    public Expr visitLiteral(Expr.Literal expr) {
        return null;
    }

    @Override
    public Expr visitVariable(Expr.Variable expr) {
        return null;
    }

    @Override
    public Expr visitUnary(Expr.Unary expr) {
        return null;
    }

    @Override
    public Expr visitBinary(Expr.Binary expr) {
        return null;
    }

    @Override
    public Expr visitCall(Expr.Call expr) {
        return null;
    }

    @Override
    public Expr visitAssignedArgument(Expr.AssignedArgument expr) {
        return null;
    }

    @Override
    public Expr visitDrawnArgument(Expr.DrawnArgument expr) {
        return null;
    }

    @Override
    public Expr visitGrouping(Expr.Grouping expr) {
        return null;
    }

    @Override
    public Expr visitArray(Expr.Array expr) {
        return null;
    }

    @Override
    public Expr visitListComprehension(Expr.ListComprehension expr) {
        return null;
    }

    @Override
    public Expr visitGet(Expr.Get expr) {
        return null;
    }

    @Override
    public AstType visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public AstType visitGenericType(AstType.Generic expr) {
        return null;
    }

    private Expr process(Expr current) {
        currentLevel++;

        if (currentLevel < 3) {
            return current.accept(this);
        }

        // we add a new statement
        String variableName = getNextAvailableVariableName("var");
        // TODO

        return new Expr.Variable(variableName);
    }

    String getNextAvailableVariableName(String variableName) {
        String nextAvailableName = variableName;
        int suffix = 2;
        while (variables.contains(nextAvailableName)) {
            nextAvailableName = variableName + suffix++;
        }
        return nextAvailableName;
    }
}
