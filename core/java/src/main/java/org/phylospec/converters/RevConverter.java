package org.phylospec.converters;

import org.phylospec.ast.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;

import java.util.*;

/// This class converts parsed PhyloSpec statements into an Rev script.
///
/// Usage:
/// ```
/// List<Stmt> statements = parser.parse();
/// String lphyString = RevConverter.convertToRev(statements, componentResolver);
///```
public class RevConverter implements AstVisitor<Void, StringBuilder, Void> {

    ComponentResolver componentResolver;
    private final StochasticityResolver stochasticityResolver;
    private final TypeResolver typeResolver;
    private final Set<String> variableNames;

    private List<String> revStatements;
    private List<String> modelVariableNames;

    /**
     * Private constructor. Use {@code RevConverter.convertToRev}.
     */
    private RevConverter(List<Stmt> statements, ComponentResolver componentResolver) {
        revStatements = new ArrayList<>();

        this.componentResolver = componentResolver;
        stochasticityResolver = new StochasticityResolver();
        typeResolver = new TypeResolver(componentResolver);

        for (Stmt stmt : statements) {
            stmt.accept(stochasticityResolver);
            stmt.accept(typeResolver);
        }

        variableNames = new HashSet<>(typeResolver.variableTypes.keySet());
        modelVariableNames = new ArrayList<>();
    }

    /**
     * Converts the given statements into a Rev script.
     */
    public static String convertToRev(String phylospecFileName, List<Stmt> statements, ComponentResolver componentResolver) {
        RevConverter converter = new RevConverter(statements, componentResolver);
        String modelName = phylospecFileName.endsWith(".phylospec")
                ? phylospecFileName.substring(0, phylospecFileName.length() - ".phylospec".length())
                : phylospecFileName;

        // traverse the syntax tree to collect the Rev statements

        for (Stmt statement : statements) {
            statement.accept(converter);
        }

        StringBuilder builder = new StringBuilder();

        // add empty moves and monitors array

        builder.append("moves = VectorMoves()\n");
        builder.append("monitors = VectorMonitors()\n\n");

        // add statements

        for (String statement : converter.revStatements) {
            builder.append(statement).append("\n");
        }

        if (!converter.modelVariableNames.isEmpty()) {

            // add monitors

            builder.append("\nmonitors.append( mnModel( filename = \"").append(modelName).append(".log\", printgen = 10 ) )\n");
            builder.append("monitors.append( mnFile( filename = \"").append(modelName).append(".trees\", printgen = 10 ) )\n");
            builder.append("monitors.append( mnScreen( printgen = 10 ) )\n\n");

            // build mcmc

            builder.append("mymodel = model( ");
            builder.append(String.join(", ", converter.modelVariableNames));
            builder.append(" )\n");
            builder.append("mymcmc = mcmc( mymodel, monitors, moves )\n");
            builder.append("mymcmc.run( generations=20000, tuningInterval=200 )\n");

        }

        // exit script

        builder.append("\nq()");

        return builder.toString();
    }

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        // make sure we have a @observedAs decorator with one argument
        if (!stmt.decorator.functionName.equals("observedAs")) {
            throw new RevConversionError("Decorator " + stmt.decorator.functionName + " is not supported in Rev.");
        }
        if (stmt.decorator.arguments.length != 1) {
            throw new RevConversionError("Decorator " + stmt.decorator.functionName + " requires exactly one argument.");
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
            throw new RevConversionError("Rev does not support nested decorators.");
        }

        // we add an additional Rev statement
        // `observedVariableName = randomVariableName;`
        // to signal the clamping
        return null;
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        StringBuilder builder = new StringBuilder();

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(stmt);
        if (stochasticity == Stochasticity.CONSTANT) {
            builder.append(sanitizeVariableName(stmt.name)).append(" <- ").append(stmt.expression.accept(this));
        } else {
            builder.append(sanitizeVariableName(stmt.name)).append(" := ").append(stmt.expression.accept(this));
        }

        revStatements.add(builder.toString());

        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(sanitizeVariableName(stmt.name)).append(" ~ ").append(stmt.expression.accept(this));
        revStatements.add(builder.toString());
        modelVariableNames.add(sanitizeVariableName(stmt.name));

        StringBuilder move = RevMoves.getMoveStatement(
                sanitizeVariableName(stmt.name), typeResolver.resolveType(stmt), componentResolver
        );
        if (move != null) {
            revStatements.add(move.toString());
        }

        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        return null;
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
            default -> throw new RevConversionError("Literal " + expr.value + " is not supported in Rev.");
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
        throw new RevConversionError("Unary operation " + TokenType.getLexeme(expr.operator) + " is not supported in Rev.");
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

        throw new RevConversionError("Binary operation " + TokenType.getLexeme(expr.operator) + " is not supported in Rev.");
    }

    @Override
    public StringBuilder visitCall(Expr.Call expr) {
        Map<String, String> arguments = new HashMap<>();
        for (Expr.Argument arg : expr.arguments) {
            arguments.put(arg.name, arg.accept(this).toString());
        }
        return RevGeneratorMapping.map(expr.functionName, arguments);
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
                .append(" ~ ").append(expr.expression.accept(this));
        revStatements.add(variableDeclaration.toString());
        modelVariableNames.add(variableName);
        StringBuilder move = RevMoves.getMoveStatement(
                variableName, typeResolver.resolveType(expr).iterator().next(), componentResolver
        );
        if (move != null) {
            revStatements.add(move.toString());
        }

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
        builder.append("v( ");

        for (int i = 0; i < expr.elements.size(); i++) {
            builder.append(expr.elements.get(i).accept(this));

            if (i < expr.elements.size() - 1) {
                builder.append(", ");
            }
        }

        builder.append(" )");
        return builder;
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

        throw new RevConversionError("Property " + expr.properyName + " is not supported in Rev.");
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
     * Rev. Returns the sanitized variable name.
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
     * Adds a new Rev statement. Depending on the stochastictity of {@code node}, it is added to the data or model
     * block.
     */
    private void addStatement(AstNode node, String variableName, StringBuilder expressionBuilder) {
        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);

        switch (stochasticity) {
            case CONSTANT -> revStatements.add(expressionBuilder.insert(0, variableName + " -> ").toString());
            case DETERMINISTIC -> revStatements.add(expressionBuilder.insert(0, variableName + " := ").toString());
            case STOCHASTIC -> revStatements.add(expressionBuilder.insert(0, variableName + "  ").toString());
        }
    }

    static class RevConversionError extends RuntimeException {
        public RevConversionError(String s) {
            super(s);
        }
    }
}
