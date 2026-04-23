package org.phylospec.typeresolver;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.AstVisitor;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;

import java.util.*;

/// Resolves variable references in an AST, mapping each {@link Expr.Variable} occurrence
/// to the {@link Stmt} that declares it.
///
/// Global variables (assignments, draws, observed, indexed) are tracked across statements.
/// Index variables introduced by {@link Stmt.Indexed} are tracked in a nested scope and
/// are not linked to any declaring statement.
///
/// Usage:
/// ```
/// List<AstNode> statements = <...>;
/// VariableResolver resolver = new VariableResolver(statements);
///
/// Stmt declaringStmt = resolver.resolveVariable(someVariableExpr);
/// ```
public class VariableResolver implements AstVisitor<Void, Void, Void> {

    private final Map<String, Stmt> resolvedGlobalVariableNames;
    private final IdentityHashMap<Expr.Variable, Stmt> resolvedVariables;

    private final List<Set<String>> scopedVariableNames;

    /**
     * Constructs a resolver by visiting all statements in the given list.
     * Later statements may refer to variables declared by earlier ones.
     */
    public VariableResolver(List<? extends AstNode> statements) {
        this.resolvedGlobalVariableNames = new HashMap<>();
        this.resolvedVariables = new IdentityHashMap<>();
        this.scopedVariableNames = new ArrayList<>();

        for (AstNode node : statements) {
            if (node instanceof Stmt stmt) stmt.accept(this);
            else if (node instanceof Expr expr) expr.accept(this);
        }
    }

    /**
     * Returns the declaring statement for the given variable expression, or {@code null}
     * if the variable was not resolved (e.g. it is a scoped index variable).
     */
    public Stmt resolveVariable(Expr.Variable variable) {
        return this.resolvedVariables.get(variable);
    }

    /**
     * Registers the assignment variable in the global scope and resolves references
     * within the right-hand side expression.
     */
    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        createScope();
        stmt.expression.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(stmt.name, stmt);
        return null;
    }

    /**
     * Registers the drawn variable in the global scope and resolves references
     * within the distribution expression.
     */
    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        createScope();
        stmt.expression.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(stmt.name, stmt);
        return null;
    }

    /**
     * Resolves references in the observed value and the inner statement, then registers
     * the observed-as wrapper as the declaring statement for the inner variable.
     */
    @Override
    public Void visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        observedAs.observedAs.accept(this);

        createScope();
        observedAs.stmt.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(observedAs.stmt.getName(), observedAs);
        return null;
    }

    /**
     * Resolves references in the observation bounds and the inner statement, then registers
     * the observed-between wrapper as the declaring statement for the inner variable.
     */
    @Override
    public Void visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        observedBetween.observedFrom.accept(this);
        observedBetween.observedTo.accept(this);

        createScope();
        observedBetween.stmt.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(observedBetween.stmt.getName(), observedBetween);
        return null;
    }

    /**
     * Resolves references in the range expressions, pushes a scope containing the index
     * variables so they are not linked to a global declaration, then registers the indexed
     * wrapper as the declaring statement for the inner variable.
     */
    @Override
    public Void visitIndexedStmt(Stmt.Indexed indexed) {
        indexed.ranges.forEach(x -> x.accept(this));
        createScope();
        for (Expr.Variable index : indexed.indices) {
            this.scopedVariableNames.getFirst().add(index.variableName);
        }
        indexed.statement.accept(this);
        dropScope();

        this.resolvedGlobalVariableNames.put(indexed.statement.getName(), indexed);
        return null;
    }

    /**
     * Links the variable expression to its declaring statement in the global scope.
     * Variables that appear in any nested scope (e.g. index variables) are skipped
     * and left unresolved.
     */
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

    /* scoping */

    private void createScope() {
        this.scopedVariableNames.addFirst(new HashSet<>());
    }

    private void dropScope() {
        this.scopedVariableNames.removeFirst();
    }
}
