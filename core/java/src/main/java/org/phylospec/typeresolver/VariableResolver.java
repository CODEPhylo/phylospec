package org.phylospec.typeresolver;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.AstVisitor;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;

import java.util.*;

public class VariableResolver implements AstVisitor<Void, Void, Void> {

    private final Map<String, Stmt> resolvedGlobalVariableNames;
    private final IdentityHashMap<Expr.Variable, Stmt> resolvedVariables;

    private final List<Set<String>> scopedVariableNames;

    public VariableResolver(List<? extends AstNode> statements) {
        this.resolvedGlobalVariableNames = new HashMap<>();
        this.resolvedVariables = new IdentityHashMap<>();
        this.scopedVariableNames = new ArrayList<>();

        for (AstNode node : statements) {
            if (node instanceof Stmt stmt) stmt.accept(this);
            else if (node instanceof Expr expr) expr.accept(this);
        }
    }

    public Stmt resolveVariable(Expr.Variable variable) {
        return this.resolvedVariables.get(variable);
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        createScope();
        stmt.expression.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(stmt.name, stmt);
        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        createScope();
        stmt.expression.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(stmt.name, stmt);
        return null;
    }

    @Override
    public Void visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        observedAs.observedAs.accept(this);

        createScope();
        observedAs.stmt.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(this.extractVariableName(observedAs.stmt), observedAs);
        return null;
    }

    @Override
    public Void visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        observedBetween.observedFrom.accept(this);
        observedBetween.observedTo.accept(this);

        createScope();
        observedBetween.stmt.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(this.extractVariableName(observedBetween.stmt), observedBetween);
        return null;
    }

    @Override
    public Void visitIndexedStmt(Stmt.Indexed indexed) {
        indexed.ranges.forEach(x -> x.accept(this));
        createScope();
        for (Expr.Variable index : indexed.indices) {
            this.scopedVariableNames.getFirst().add(index.variableName);
        }
        indexed.statement.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(this.extractVariableName(indexed.statement), indexed);
        return null;
    }

    private String extractVariableName(Stmt stmt) {
        if (stmt instanceof Stmt.Assignment a) return a.name;
        if (stmt instanceof Stmt.Draw d) return d.name;
        if (stmt instanceof Stmt.Decorated decorated) return extractVariableName(decorated.statement);
        return null;
    }

    @Override
    public Void visitVariable(Expr.Variable expr) {
        // if this variable corresponds to a variable in any of the non-global scopes, we don't remember it

        for (Set<String> scope : this.scopedVariableNames) {
            if (scope.contains(expr.variableName)) {
                // the variable is from a scope which is not the global one
                // we don't remember it
                return null;
            }
        }

        // the variable is in the global scope
        // store the reference of the AstNode to the defining Stmt

        Stmt variableDefinition = this.resolvedGlobalVariableNames.get(expr.variableName);
        this.resolvedVariables.put(expr, variableDefinition);

        return null;
    }

    private void createScope() {
        this.scopedVariableNames.addFirst(new HashSet<>());
    }

    private void dropScope() {
        this.scopedVariableNames.removeFirst();
    }
}
