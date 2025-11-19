package org.phylospec.converters;

import org.phylospec.ast.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;

import java.util.*;

/// This class converts parsed PhyloSpec statements into an LPhy script.
///
/// Usage:
/// ```
/// List<Stmt> statements = parser.parse();
/// String lphyString = LPhyConverter.convertToLPhy(statements, componentResolver);
///```
///
public class LPhyConverter implements AstVisitor<StringBuilder, StringBuilder, Void> {

    private final List<String> dataStatements;
    private final List<String> modelStatements;
    private final StochasticityResolver stochasticityResolver;
    private final TypeResolver typeResolver;
    private final Set<String> variableNames;

    /**
     * Private constructor. Use {@code LPhyConverter.convertToLPhy}.
     */
    private LPhyConverter(List<Stmt> statements, ComponentResolver componentResolver) {
        dataStatements = new ArrayList<>();
        modelStatements = new ArrayList<>();

        stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        variableNames = typeResolver.getVariableNames();
    }

    /**
     * Converts the given statements into an LPhy script.
     */
    public static String convertToLPhy(List<Stmt> statements, ComponentResolver componentResolver) {
        LPhyConverter converter = new LPhyConverter(statements, componentResolver);

        // traverse the syntax tree to collect the LPhy statements
        for (Stmt statement : statements) {
            statement.accept(converter);
        }

        StringBuilder result = new StringBuilder();

        // write the data block
        result.append("data {\n");
        for (String dataStatement : converter.dataStatements) {
            result.append("\t").append(dataStatement).append("\n");
        }
        result.append("}\n");

        // write the model block
        result.append("model {\n");
        for (String modelStatement : converter.modelStatements) {
            result.append("\t").append(modelStatement).append("\n");
        }
        result.append("}");

        return result.toString();
    }

    @Override
    public StringBuilder visitDecoratedStmt(Stmt.Decorated stmt) {
        // make sure we have a @observedAs decorator with one argument
        if (!stmt.decorator.functionName.equals("observedAs")) {
            throw new LPhyConversionError("Decorator " + stmt.decorator.functionName + " is not supported in LPhy.");
        }
        if (stmt.decorator.arguments.length != 1) {
            throw new LPhyConversionError("Decorator " + stmt.decorator.functionName + " requires exactly one argument.");
        }

        stmt.statement.accept(this);

        // this is an @observedAs decorator of the form
        // @observedAs(<observedVariableName>)
        // Type <randomVariableName> ~ ...

        String observedVariableName = stmt.decorator.arguments[0].accept(this).toString();

        String randomVariableName;
        if (stmt.statement instanceof Stmt.Draw) {
            randomVariableName = ((Stmt.Draw) stmt.statement).name;
        } else if (stmt.statement instanceof Stmt.Assignment) {
            randomVariableName = ((Stmt.Draw) stmt.statement).name;
        } else {
            throw new LPhyConversionError("LPhy does not support nested decorators.");
        }

        // we add an additional LPhy statement
        // `observedVariableName = randomVariableName;`
        // to signal the clamping
        return addStatement(
                stmt,
                new StringBuilder(observedVariableName).append(" = ").append(randomVariableName).append(";")
        );
    }

    @Override
    public StringBuilder visitAssignment(Stmt.Assignment stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(sanitizeVariableName(stmt.name)).append(" = ").append(stmt.expression.accept(this)).append(";");
        return addStatement(stmt, builder);
    }

    @Override
    public StringBuilder visitDraw(Stmt.Draw stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(sanitizeVariableName(stmt.name)).append(" ~ ").append(stmt.expression.accept(this)).append(";");
        return addStatement(stmt, builder);
    }

    @Override
    public StringBuilder visitImport(Stmt.Import stmt) {
        return new StringBuilder();
    }

    @Override
    public StringBuilder visitLiteral(Expr.Literal expr) {
        return switch (expr.value) {
            case Integer value -> new StringBuilder(value.toString());
            case Long value -> new StringBuilder(value.toString());
            case Float value -> new StringBuilder(value.toString());
            case Double value -> new StringBuilder(value.toString());
            case String value -> new StringBuilder("\"").append(value).append("\"");
            case Boolean value -> new StringBuilder(value.toString());
            default -> throw new LPhyConversionError("Literal " + expr.value + " is not supported in LPhy.");
        };
    }

    @Override
    public StringBuilder visitVariable(Expr.Variable expr) {
        return new StringBuilder(sanitizeVariableName(expr.variableName));
    }

    @Override
    public StringBuilder visitUnary(Expr.Unary expr) {
        if (expr.operator == TokenType.MINUS) {
            return new StringBuilder().append("-").append(expr.right.accept(this));
        }
        throw new LPhyConversionError("Unary operation " + TokenType.getLexeme(expr.operator) + " is not supported in LPhy.");
    }

    @Override
    public StringBuilder visitBinary(Expr.Binary expr) {
        StringBuilder left = expr.left.accept(this);
        StringBuilder right = expr.right.accept(this);

        if (expr.operator == TokenType.MINUS) {
            return new StringBuilder().append(left).append(" - ").append(right);
        } else if (expr.operator == TokenType.PLUS) {
            return new StringBuilder().append(left).append(" + ").append(right);
        } else if (expr.operator == TokenType.STAR) {
            return new StringBuilder().append(left).append(" * ").append(right);
        } else if (expr.operator == TokenType.SLASH) {
            return new StringBuilder().append(left).append(" / ").append(right);
        }

        throw new LPhyConversionError("Binary operation " + TokenType.getLexeme(expr.operator) + " is not supported in LPhy.");
    }

    @Override
    public StringBuilder visitCall(Expr.Call expr) {
        Map<String, String> arguments = new HashMap<>();
        for (Expr.Argument arg : expr.arguments) {
            arguments.put(arg.name, arg.accept(this).toString());
        }
        return LPhyGeneratorMapping.map(expr.functionName, arguments);
    }

    @Override
    public StringBuilder visitAssignedArgument(Expr.AssignedArgument expr) {
        return expr.expression.accept(this);
    }

    @Override
    public StringBuilder visitDrawnArgument(Expr.DrawnArgument expr) {
        // we add a separate variable with the result of the draw
        String variableName = getAvailableVariableName(expr.name);
        StringBuilder variableDeclaration = new StringBuilder(variableName)
                .append(" ~ ").append(expr.expression.accept(this)).append(";");
        addStatement(expr, variableDeclaration);

        // we now pass the new variable to the function
        return new StringBuilder(variableName);
    }

    @Override
    public StringBuilder visitGrouping(Expr.Grouping expr) {
        return new StringBuilder().append("(").append(expr.expression.accept(this)).append(")");
    }

    @Override
    public StringBuilder visitArray(Expr.Array expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");

        for (int i = 0; i < expr.elements.size(); i++) {
            builder.append(expr.elements.get(i).accept(this));

            if (i < expr.elements.size() - 1) {
                builder.append(", ");
            }
        }

        builder.append("]");
        return builder;
    }

    @Override
    public StringBuilder visitListComprehension(Expr.ListComprehension expr) {
        return null;
    }

    @Override
    public StringBuilder visitGet(Expr.Get expr) {
        StringBuilder objectBuilder = expr.object.accept(this);

        Set<ResolvedType> objectTypeSet = this.typeResolver.resolveType(expr.object);
        for (ResolvedType candidateType : objectTypeSet) {
            String componentName = candidateType.getName();
            StringBuilder mappedMethod = LPhyMethodsMapping.map(componentName, expr.properyName, objectBuilder);

            if (mappedMethod != null) {
                return mappedMethod;
            }
        }

        throw new LPhyConversionError("Property " + expr.properyName + " is not supported in LPhy.");
    }

    @Override
    public Void visitAtomicType(AstType.Atomic expr) {
        return null;
    }

    @Override
    public Void visitGenericType(AstType.Generic expr) {
        return null;
    }

    /**
     * Makes sure that the variable name does not equal to "model" or "code", as these are reserved in
     * LPhy. Returns the sanitized variable name.
     */
    private String sanitizeVariableName(String variableName) {
        if (variableName.equals("data") || variableName.equals("model")) {
           while (variableNames.contains(variableName)) {
               variableName = variableName + "_";
           }
           return variableName;
        }
        return variableName;
    }

    /**
     * Returns {@code proposedName} if no other variable like it exists. Otherwise, adds a suffix {@code proposedName}
     * and returns the available name.
     * Note that this currently breaks if there is a `data` variable sanitized to `data_` by {@code sanitizeVariableName}
     * and a function taking a drawn argument `data_` *not* changed by {@code getAvailableVariableName}.
     */
    private String getAvailableVariableName(String proposedName) {
        if (!variableNames.contains(proposedName)) return proposedName;

        int suffix = 2;
        String adjustedProposedName = proposedName + suffix;
        while (this.variableNames.contains(adjustedProposedName)) {
            adjustedProposedName = proposedName + ++suffix;
        }

        variableNames.add(adjustedProposedName);

        return adjustedProposedName;
    }

    /**
     * Adds a new LPhy statement. Depending on the stochasticity of {@code node}, it is added to the data or model
     * block.
     */
    private StringBuilder addStatement(AstNode node, StringBuilder builder) {
        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);

        if (stochasticity == Stochasticity.STOCHASTIC) {
            modelStatements.add(builder.toString());
        } else  {
            dataStatements.add(builder.toString());
        }

        return builder;
    }

    static class LPhyConversionError extends RuntimeException {
        public LPhyConversionError(String s) {
            super(s);
        }
    }
}
