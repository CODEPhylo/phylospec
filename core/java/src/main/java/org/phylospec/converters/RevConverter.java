package org.phylospec.converters;

import org.phylospec.ast.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.TokenType;
import org.phylospec.typeresolver.*;

import java.util.*;

/// This class converts parsed PhyloSpec statements into an Rev script.
///
/// Usage:
/// ```
/// List<Stmt> statements = parser.parse();
/// String lphyString = RevConverter.convertToRev(statements, componentResolver);
///```
public class RevConverter implements AstVisitor<Void, StringBuilder, Void> {

    private final ComponentResolver componentResolver;
    private final StochasticityResolver stochasticityResolver;
    private final TypeResolver typeResolver;

    private final List<RevStmt> revStatements;

    private final List<HashMap<String, String>> scopedVariableAliases;

    /**
     * Private constructor. Use {@code RevConverter.convertToRev}.
     */
    private RevConverter(List<Stmt> statements, ComponentResolver componentResolver) {
        revStatements = new ArrayList<>();

        this.componentResolver = componentResolver;

        stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        scopedVariableAliases = new ArrayList<>();
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
            randomVariableName + ".clamp( " + observedVariableName + " )"
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
        ResolvedType type = typeResolver.resolveType(stmt);

        Set<ResolvedType> expressionTypeSet = typeResolver.resolveType(stmt.expression);
        Stochasticity[] stochasticity = new Stochasticity[] {Stochasticity.DETERMINISTIC};
        for (ResolvedType expressionType : expressionTypeSet) {
            TypeUtils.visitTypeAndParents(expressionType, t -> {
                if (t.getName().equals("Distribution")) {
                    stochasticity[0] = Stochasticity.STOCHASTIC;
                    return TypeUtils.Visitor.STOP;
                }
                return TypeUtils.Visitor.CONTINUE;
            }, componentResolver);
        }

        addStatement(stmt.name, stochasticity[0], type, expression);

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
        // we see if there is any alias in the current scope
        for (HashMap<String, String> scopedAliases : scopedVariableAliases) {
            if (scopedAliases.containsKey(expr.variableName)) {
                return new StringBuilder(scopedAliases.get(expr.variableName));
            }
        }

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
        Map<String, String> arguments = new HashMap<>();
        for (Expr.Argument arg : expr.arguments) {
            arguments.put(arg.name, arg.accept(this).toString());
        }
        return RevGeneratorMapping.map(expr, arguments, this);
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
    public StringBuilder visitListComprehension(Expr.ListComprehension expr) {
        StringBuilder list = expr.list.accept(this);

        if (expr.variables.size() == 1) {
            // we have a = [expr(x) for x in list]
            //
            // we convert this into:
            //
            // temp_list := list                    # listStmt
            // for (i in 1:temp_list.size()) {
            // temp_expr[i] := expr(temp_list[i])   # expressionStmt
            // }
            // a = temp_expr

            // add list statement

            RevStmt.Assignment listStmt = addStatement(
                    new RevStmt.Assignment("temp_list", list)
            );
            String listVarName = listStmt.variableName;

            // start for loop

            String indexVarName = getNextAvailableVariableName("i");
            addStatement(new RevStmt("for (" + indexVarName + " in 1:" + listVarName + ".size()) {"));

            // evaluate expr with the list comprehension

            createVariableScope();

            addAliasedVariableToScope(expr.variables.getFirst(), listVarName + "[" + indexVarName + "]");
            addVariableToScope(indexVarName);

            StringBuilder expression = expr.expression.accept(this);
            RevStmt.Assignment expressionStmt = addStatement(
                    new RevStmt.Assignment("temp_expr", new String[] {indexVarName}, expression)
            );
            String expressionVarName = expressionStmt.variableName;

            dropVariableScope();

            // end for loop

            addStatement(new RevStmt("}"));

            // expressionVarName is now at the place of the original expression
            return new StringBuilder(expressionVarName);
        }

        if (expr.variables.size() == 2) {
            // we have a = [expr(x, y) for x, y in list]
            //
            // we convert this into:
            //
            // temp_list := list                    # listStmt
            // for (i in 1:temp_list.size()) {
            // temp_expr[i] := expr(temp_list[i][1], temp_list[i][2])   # expressionStmt
            // }
            // a = temp_expr

            // add list statement

            RevStmt.Assignment listStmt = addStatement(
                    new RevStmt.Assignment("temp_list", list)
            );
            String listVarName = listStmt.variableName;

            // start for loop

            String indexVarName = getNextAvailableVariableName("i");
            addStatement(new RevStmt("for (" + indexVarName + " in 1:" + listVarName + ".size()) {"));

            // evaluate expr with the list comprehension

            createVariableScope();

            addAliasedVariableToScope(expr.variables.getFirst(), listVarName + "[" + indexVarName + "][1]");
            addAliasedVariableToScope(expr.variables.getLast(), listVarName + "[" + indexVarName + "][2]");
            addVariableToScope(indexVarName);

            StringBuilder expression = expr.expression.accept(this);
            RevStmt.Assignment expressionStmt = addStatement(
                    new RevStmt.Assignment("temp_expr", new String[] {indexVarName}, expression)
            );
            String expressionVarName = expressionStmt.variableName;

            dropVariableScope();

            // end for loop

            addStatement(new RevStmt("}"));

            // expressionVarName is now at the place of the original expression
            return new StringBuilder(expressionVarName);
        }

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

    RevStmt addStatement(RevStmt stmt) {
        revStatements.add(stmt);
        return stmt;
    }

    RevStmt.Assignment addStatement(RevStmt.Assignment stmt) {
        return addStatement(stmt, true);
    }

    RevStmt.Assignment addStatement(RevStmt.Assignment stmt, boolean preventNameConflict) {
        if (preventNameConflict) {
            stmt.variableName = getNextAvailableVariableName(stmt.variableName);
        }
        revStatements.add(stmt);
        return stmt;
    }

    RevStmt.Assignment addStatement(String variableName, Stochasticity stochasticity, ResolvedType type, StringBuilder expression) {
        return addStatement(variableName, new String[] {}, stochasticity, type, expression);
    }

    RevStmt.Assignment addStatement(String variableName, String[] indices, Stochasticity stochasticity, ResolvedType type, StringBuilder expression) {
        return addStatement(
                new RevStmt.Assignment(variableName, indices, stochasticity, type, expression, componentResolver)
        );
    }

    String getNextAvailableVariableName(String variableName) {
        String nextAvailableName = variableName;
        int suffix = 2;
        while (isVariableNameInUse(nextAvailableName)) {
            nextAvailableName = variableName + suffix++;
        }
        return nextAvailableName;
    }
    private boolean isVariableNameInUse(String variableName) {
        List<String> takenVariableNames = revStatements.stream()
                .filter(s -> s instanceof RevStmt.Assignment)
                .map(s -> ((RevStmt.Assignment) s).variableName)
                .toList();
        return (
                takenVariableNames.contains(variableName) ||
                scopedVariableAliases.stream().anyMatch(x -> x.containsValue(variableName))
        );
    }

    private Stochasticity getStochasticity(AstNode expr) {
        return stochasticityResolver.getStochasticity(expr);
    }

    private void createVariableScope() {
        scopedVariableAliases.addFirst(new HashMap<>());
    }
    private void dropVariableScope() {
        scopedVariableAliases.removeFirst();
    }
    private void addVariableToScope(String variableName) {
        scopedVariableAliases.getFirst().put(variableName, variableName);
    }
    private void addAliasedVariableToScope(String variableName, String alias) {
        scopedVariableAliases.getFirst().put(variableName, alias);
    }

    static class RevConversionError extends RuntimeException {
        public RevConversionError(String s) {
            super(s);
        }
    }
}
