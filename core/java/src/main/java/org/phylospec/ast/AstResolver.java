package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;

import java.util.HashMap;
import java.util.Map;

/// This class traverses an AST statement and resolves each variable and type.
///
/// Each variable gets mapped to either its declaration statement (for
/// local variables), or to its generator from a component library.
/// Each type gets mapped to a type from a component library.
///
/// This class uses the visitor pattern to traverse the statement. It
/// has internal state, such that multiple consecutive statements can
/// be visited one after another, with later statements referring to
/// previous ones.
///
/// Usage:
/// ```
/// Stmt statement1 = <...>;
/// Stmt statement2 = <...>;
/// AstResolver resolver = new AstResolver(...);
/// statement1.accept(resolver);
/// statement2.accept(resolver);
/// ```
public class AstResolver implements AstVisitor<Void, Void, Void> {

    private ComponentResolver componentResolver;

    private Map<String, ResolvedVariable> variableMapping;
    private Map<String, org.phylospec.components.Type> typeMapping;

    public AstResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.variableMapping = new HashMap<>();
        this.typeMapping = new HashMap<>();
    }

    @Override
    public Void visitVariable(Expr.Variable expr) {
        String variableName = expr.variable;

        // we first check if the name matches a variable we've seen before
        if (variableMapping.containsKey(variableName)) {
            return null;
        }

        // otherwise, we check if it matches a known component
        if (componentResolver.canResolveGenerator(variableName)) {
            Generator generator = componentResolver.resolveGenerator(variableName);
            ResolvedGenerator resolvedGenerator = new  ResolvedGenerator(generator);
            variableMapping.put(variableName, resolvedGenerator);
            return null;
        }

        throw new ResolutionError("Variable " + variableName + " is not known");
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        stmt.expression.accept(this);

        // we resolve the type first
        String typeName = stmt.type.name;
        if (!componentResolver.canResolveType(typeName)) {
            throw new ResolutionError("Type " + typeName + " is not known");
        }
        org.phylospec.components.Type type = componentResolver.resolveType(typeName);
        typeMapping.put(typeName, type);

        // we then add the assigned variable to our mapping
        String variableName = stmt.name;
        ResolvedLocalVariable resolvedLocalVariable = new ResolvedLocalVariable(stmt, type);
        variableMapping.put(variableName, resolvedLocalVariable);

        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        stmt.expression.accept(this);

        // we resolve the type first
        String typeName = stmt.type.name;
        if (!componentResolver.canResolveType(typeName)) {
            throw new ResolutionError("Type " + typeName + " is not known");
        }
        org.phylospec.components.Type type = componentResolver.resolveType(typeName);
        typeMapping.put(typeName, type);

        // we then add the assigned variable to our mapping
        String variableName = stmt.name;
        ResolvedLocalVariable resolvedLocalVariable = new ResolvedLocalVariable(stmt, type);
        variableMapping.put(variableName, resolvedLocalVariable);

        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        componentResolver.importNamespace(stmt.namespace);
        return null;
    }

    /** Boilerplate Visitors (these don't change the variableMapping but simply
     *  recursively accept the subtree) */

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.statememt.accept(this);
        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        expr.right.accept(this);
        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return null;
    }

    @Override
    public Void visitCall(Expr.Call expr) {
        expr.function.accept(this);

        for (Expr.Argument argument : expr.arguments) {
            argument.accept(this);
        }

        return null;
    }

    @Override
    public Void visitAssignedArgument(Expr.AssignedArgument expr) {
        expr.expression.accept(this);
        return null;
    }

    @Override
    public Void visitDrawnArgument(Expr.DrawnArgument expr) {
        expr.expression.accept(this);
        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        expr.expression.accept(this);
        return null;
    }

    @Override
    public Void visitArray(Expr.Array expr) {
        for (Expr element : expr.elements) {
            element.accept(this);
        }
        return null;
    }

    @Override
    public Void visitGet(Expr.Get expr) {
        expr.object.accept(this);
        return null;
    }

    @Override
    public Void visitAtomicType(Type.Atomic expr) {
        return null;
    }

    @Override
    public Void visitGenericType(Type.Generic expr) {
        for (Type type : expr.typeParameters) {
            type.accept(this);
        }
        return null;
    }

    /** ResolvedVariable classes */

    public abstract class ResolvedVariable {
    }

    public class ResolvedGenerator extends ResolvedVariable {
        private Generator generator;

        public ResolvedGenerator(Generator generator) {
        }

        public Generator getGenerator() {
            return generator;
        }
    }

    public class ResolvedLocalVariable extends ResolvedVariable {
        private Stmt statement;
        private org.phylospec.components.Type resolvedType;

        public ResolvedLocalVariable(Stmt statement, org.phylospec.components.Type resolvedType) {
            this.statement = statement;
        }

        public Stmt getStatement() {
            return statement;
        }

        public org.phylospec.components.Type getResolvedType() {
            return resolvedType;
        }
    }

    private class ResolutionError extends RuntimeException {
        public ResolutionError(String s) {
            super(s);
        }
    }
}