package org.phylospec.typeresolver;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalLong;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.Dimension;

/**
 * Infers one-dimensional size expressions from the PhyloSpec AST.
 *
 * This is intentionally conservative. It only returns literal dimensions when
 * they can be determined without evaluating backend objects. Later stages can
 * extend this with stronger symbolic unification.
 */
public class DimensionResolver {

    private final VariableResolver variableResolver;
    private final Map<AstNode, Dimension> cache = new IdentityHashMap<>();
    private final Map<AstNode, Boolean> visiting = new IdentityHashMap<>();

    public DimensionResolver(VariableResolver variableResolver) {
        this.variableResolver = variableResolver;
    }

    public Dimension getDimension(AstNode node) {
        if (node == null) {
            return Dimension.unknown();
        }

        Dimension cached = this.cache.get(node);
        if (cached != null) {
            return cached;
        }

        if (this.visiting.containsKey(node)) {
            return Dimension.unknown();
        }

        this.visiting.put(node, true);

        Dimension dimension = inferDimension(node);

        this.visiting.remove(node);
        this.cache.put(node, dimension);

        return dimension;
    }

    private Dimension inferDimension(AstNode node) {
        if (node instanceof Expr.Array array) {
            return Dimension.literal(array.elements.size());
        }

        if (node instanceof Expr.AssignedArgument argument) {
            return getDimension(argument.expression);
        }

        if (node instanceof Expr.DrawnArgument argument) {
            return getDimension(argument.expression);
        }

        if (node instanceof Expr.Grouping grouping) {
            return getDimension(grouping.expression);
        }

        if (node instanceof Expr.Variable variable) {
            return inferVariableDimension(variable);
        }

        if (node instanceof Expr.Call call) {
            return inferCallDimension(call);
        }

        if (node instanceof Stmt.Assignment assignment) {
            return getDimension(assignment.expression);
        }

        if (node instanceof Stmt.Draw draw) {
            return getDimension(draw.expression);
        }

        return Dimension.unknown();
    }

    private Dimension inferVariableDimension(Expr.Variable variable) {
        Stmt definition = this.variableResolver.resolveVariable(variable);

        if (definition == null) {
            return Dimension.unknown();
        }

        return getDimension(definition);
    }

    private Dimension inferCallDimension(Expr.Call call) {
        if (call.functionName.equals("repeat")) {
            return dimensionFromNumArgument(call);
        }

        if (call.functionName.equals("IID")) {
            return dimensionFromNumArgument(call);
        }

        if (call.functionName.equals("linspace")) {
            return dimensionFromNumArgument(call);
        }

        if (call.functionName.equals("Dirichlet")) {
            Expr concentration = argumentExpression(call, "concentration", 0);
            return getDimension(concentration);
        }

        return Dimension.unknown();
    }

    private Dimension dimensionFromNumArgument(Expr.Call call) {
        Expr numExpression = argumentExpression(call, "num", 1);

        if (numExpression == null) {
            return Dimension.unknown();
        }

        OptionalLong literalNum = literalIntegerValue(numExpression);

        if (literalNum.isPresent()) {
            return Dimension.literal(literalNum.getAsLong());
        }

        return Dimension.symbolic(displayExpression(numExpression));
    }

    private Expr argumentExpression(Expr.Call call, String argumentName, int positionalIndex) {
        for (Expr.Argument argument : call.arguments) {
            if (argumentName.equals(argument.name)) {
                return argument.expression;
            }
        }

        if (positionalIndex >= 0 && positionalIndex < call.arguments.length) {
            return call.arguments[positionalIndex].expression;
        }

        return null;
    }

    private OptionalLong literalIntegerValue(Expr expression) {
        if (expression instanceof Expr.Literal literal && literal.value instanceof Integer value) {
            return OptionalLong.of(value);
        }

        if (expression instanceof Expr.AssignedArgument argument) {
            return literalIntegerValue(argument.expression);
        }

        if (expression instanceof Expr.Grouping grouping) {
            return literalIntegerValue(grouping.expression);
        }

        if (expression instanceof Expr.Variable variable) {
            Stmt definition = this.variableResolver.resolveVariable(variable);

            if (definition instanceof Stmt.Assignment assignment) {
                return literalIntegerValue(assignment.expression);
            }
        }

        return OptionalLong.empty();
    }

    private String displayExpression(Expr expression) {
        if (expression instanceof Expr.Variable variable) {
            return variable.variableName;
        }

        if (expression instanceof Expr.Literal literal) {
            return literal.value.toString();
        }

        if (expression instanceof Expr.Call call) {
            return call.functionName + "(...)";
        }

        return expression.getClass().getSimpleName();
    }
}
