package org.phylospec.converters;

import org.phylospec.ast.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;

import java.util.*;
import java.util.stream.Stream;

/// This class converts parsed PhyloSpec statements into an Rev script.
///
/// Usage:
/// ```
/// List<Stmt> statements = parser.parse();
/// String lphyString = RevConverter.convertToRev(statements, componentResolver);
///```
public class RevConverter implements AstVisitor<Void, StringBuilder, Void> {

    final ComponentResolver componentResolver;
    private final StochasticityResolver stochasticityResolver;
    private final TypeResolver typeResolver;

    private final Map<Expr, Set<ResolvedType>> overwrittenResolvedTypes;
    private final Map<AstNode, Stochasticity> overwrittenStochasticities;
    private final List<RevStmt> revStatements;

    /**
     * Private constructor. Use {@code RevConverter.convertToRev}.
     */
    private RevConverter(List<Stmt> statements, ComponentResolver componentResolver) {
        revStatements = new ArrayList<>();
        overwrittenResolvedTypes = new HashMap<>();
        overwrittenStochasticities = new HashMap<>();

        this.componentResolver = componentResolver;
        stochasticityResolver = new StochasticityResolver();
        typeResolver = new TypeResolver(componentResolver);

        for (Stmt stmt : statements) {
            stmt.accept(stochasticityResolver);
            stmt.accept(typeResolver);
        }
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

        for (RevStmt statement : converter.revStatements) {
            builder.append(statement.build()).append("\n");
        }

        List<String> modelVariableNames = converter.revStatements.stream()
                .filter(s -> s instanceof RevStmt.Assignment)
                .map(s -> (RevStmt.Assignment) s)
                .filter(s -> s.stochasticity == Stochasticity.STOCHASTIC)
                .map(s -> s.variableName)
                .toList();

        if (!modelVariableNames.isEmpty()) {
            // add monitors

            builder.append("\nmonitors.append( mnModel( filename = \"").append(modelName).append(".log\", printgen = 10 ) )\n");
            builder.append("monitors.append( mnFile( filename = \"").append(modelName).append(".trees\", printgen = 10 ) )\n");
            builder.append("monitors.append( mnScreen( printgen = 10 ) )\n\n");

            // build mcmc

            builder.append("mymodel = model( ");
            builder.append(String.join(", ", modelVariableNames));
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

        // we add a Rev statement
        // randomVariableName.clamp(observedVariableName)
        // to signal the clamping
        revStatements.add(new RevStmt(
            new StringBuilder(randomVariableName).append(".clamp( ").append(observedVariableName).append(" )")
        ));
        return null;
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        StringBuilder expression = stmt.expression.accept(this);
        Stochasticity stochasticity = getStochasticity(stmt);
        ResolvedType type = typeResolver.resolveType(stmt);

        addStatement(stmt.name, stochasticity, type, expression);

        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        StringBuilder expression = stmt.expression.accept(this);
        Stochasticity stochasticity = getStochasticity(stmt.expression);
        ResolvedType type = typeResolver.resolveType(stmt);

        addStatement(stmt.name, stochasticity, type, expression);

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
        return new StringBuilder(expr.variableName);
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
        return RevGeneratorMapping.map(expr, this);
    }

    @Override
    public StringBuilder visitAssignedArgument(Expr.AssignedArgument expr) {
        return expr.expression.accept(this);
    }

    @Override
    public StringBuilder visitDrawnArgument(Expr.DrawnArgument expr) {
        // we add a separate variable with the result of the draw
        String variableName = expr.name;
        ResolvedType type = typeResolver.resolveType(expr).iterator().next();
        StringBuilder variableDeclaration = expr.expression.accept(this);
        RevStmt.Assignment stmt = addStatement(variableName, Stochasticity.STOCHASTIC, type, variableDeclaration);

        // we now pass the new variable to the function
        return new StringBuilder(stmt.variableName);
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

    void addStatement(RevStmt stmt) {
        if (stmt instanceof RevStmt.Assignment) {
            RevStmt.Assignment assignment = (RevStmt.Assignment) stmt;
            addStatement(assignment.variableName, assignment.stochasticity, assignment.type, assignment.expression);
        } else {
            revStatements.add(stmt);
        }
    }

    RevStmt.Assignment addStatement(String variableName, Stochasticity stochasticity, ResolvedType type, StringBuilder expression) {
        return addStatement(variableName, null, stochasticity, type, expression);
    }

    RevStmt.Assignment addStatement(String variableName, String index, Stochasticity stochasticity, ResolvedType type, StringBuilder expression) {
        List<String> takenVariableNames = revStatements.stream()
                .filter(s -> s instanceof RevStmt.Assignment)
                .map(s -> ((RevStmt.Assignment) s).variableName)
                .toList();

        String nextAvailableName = variableName;
        int suffix = 2;
        while (takenVariableNames.contains(nextAvailableName)) {
            nextAvailableName = variableName + suffix++;
        }

        RevStmt.Assignment stmt = new RevStmt.Assignment(
                nextAvailableName, index, stochasticity, type, expression, componentResolver
        );
        revStatements.add(stmt);

        return stmt;
    }

    Set<ResolvedType> getResolvedType(Expr expr) {
        return overwrittenResolvedTypes.getOrDefault(expr, typeResolver.resolveType(expr));
    }
    Stochasticity getStochasticity(AstNode expr) {
        return overwrittenStochasticities.getOrDefault(expr, stochasticityResolver.getStochasticity(expr));
    }

    void overwriteResolvedType(Expr expr, Set<ResolvedType> typeSet, Stochasticity stochasticity) {
        overwrittenResolvedTypes.put(expr, typeSet);
        overwrittenStochasticities.put(expr, stochasticity);
    }

    static class RevConversionError extends RuntimeException {
        public RevConversionError(String s) {
            super(s);
        }
    }
}
