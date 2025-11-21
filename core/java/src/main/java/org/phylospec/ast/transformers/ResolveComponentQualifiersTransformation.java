package org.phylospec.ast.transformers;

import org.phylospec.ast.AstTransformer;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Type;

import java.util.List;

/**
 * This transformation replaces all type names and function names with the full
 * qualifier including their namespace.
 */
public class ResolveComponentQualifiersTransformation extends AstTransformer {

    private final ComponentResolver componentResolver;

    public ResolveComponentQualifiersTransformation(List<ComponentLibrary> componentLibraries) {
        this.componentResolver = new ComponentResolver(componentLibraries);
    }

    @Override
    public Stmt visitImport(Stmt.Import stmt) {
        componentResolver.importNamespace(stmt.namespace);
        return stmt;
    }

    @Override
    public Expr visitCall(Expr.Call expr) {
        String generatorName = expr.functionName;
        List<Generator> generators = componentResolver.resolveGenerator(generatorName);
        if (generators.isEmpty()) throw new ComponentResolutionError("Function `" + generatorName + "` is not known");

        // all imported generators of this name have the same namespace
        expr.functionName = generators.getFirst().getNamespace() + "." + generators.getFirst().getName();

        return expr;
    }

    @Override
    public AstType visitAtomicType(AstType.Atomic expr) {
        String typeName = expr.name;
        Type typeComponent = componentResolver.resolveType(typeName);
        if (typeComponent == null) throw new ComponentResolutionError("Type `" + typeName + "` is not known");

        expr.name = typeComponent.getNamespace() + "." + typeComponent.getName();

        return expr;
    }

    @Override
    public AstType visitGenericType(AstType.Generic expr) {
        super.visitGenericType(expr);

        String typeName = expr.name;
        Type typeComponent = componentResolver.resolveType(typeName);
        if (typeComponent == null) throw new ComponentResolutionError("Type `" + typeName + "` is not known");

        expr.name = typeComponent.getNamespace() + "." + typeComponent.getName();

        return expr;
    }

    public static class ComponentResolutionError extends RuntimeException {
        ComponentResolutionError(String message) {
            super(message);
        }
    }
}
