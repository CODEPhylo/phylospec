package org.phylospec.ast;

import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Type;
import org.phylospec.lexer.TokenType;

import java.util.HashMap;
import java.util.List;
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
///
/// AstResolver resolver = new AstResolver(...);
/// statement1.accept(resolver);
/// statement2.accept(resolver);
///
/// ResolvedVariable var = resolver.resolveVariable("myVariableName");
/// Type var = resolver.resolveType("myTypeName");
///```
public class TypeResolver implements AstVisitor<Void, Type, Type> {

    private ComponentResolver componentResolver;
    private TypeMatcher typeMatcher;

    Map<Expr, Type> resolvedTypes;
    Map<String, Type> variableTypes;

    AstPrinter printer;

    public TypeResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.typeMatcher = new TypeMatcher(componentResolver);
        this.resolvedTypes = new HashMap<>();
        this.variableTypes = new HashMap<>();
        this.printer = new AstPrinter();
    }

    @Override
    public Void visitDecoratedStmt(Stmt.Decorated stmt) {
        stmt.statememt.accept(this);
        return null;
    }

    @Override
    public Void visitAssignment(Stmt.Assignment stmt) {
        Type resolvedExpressionType = stmt.expression.accept(this);
        Type resolvedVariableType = stmt.type.accept(this);

        assertCompatibility(resolvedVariableType, resolvedExpressionType);

        remember(stmt.name, resolvedVariableType);
        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        Type resolvedExpressionType = stmt.expression.accept(this);
        Type resolvedVariableType = stmt.type.accept(this);

        assertCompatibility(resolvedVariableType, resolvedExpressionType);

        remember(stmt.name, resolvedVariableType);
        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        return null;
    }

    @Override
    public Type visitLiteral(Expr.Literal expr) {
        String typeName = switch (expr.value) {
            case String ignored -> "String";
            case Integer ignored -> "Integer";
            case Long ignored -> "Integer";
            case Float value -> 0 < value ? "PositiveReal" : "Real";
            case Double value -> 0 < value ? "PositiveReal" : "Real";
            default -> null;
        };
        Type resolvedType = componentResolver.resolveType(typeName);

        return remember(expr, resolvedType);
    }

    @Override
    public Type visitVariable(Expr.Variable expr) {
        String variableName = expr.variableName;

        // we first check if the variable corresponds to a known local variable
        // this would shadow an imported variable
        Type resolvedType = variableTypes.get(variableName);

        if (resolvedType == null) {
            // if not, we check if the variable was imported
            resolvedType = componentResolver.resolveType(variableName);
        }

        if (resolvedType == null) {
            throw new TypeError("Unknown variable: " + variableName);
        }

        return remember(expr, resolvedType);
    }

    @Override
    public Type visitUnary(Expr.Unary expr) {
        List<List<Object>> typeMap = List.of(
                List.of(TokenType.BANG, "Boolean", "Boolean"),
                List.of(TokenType.MINUS, "Real", "Real"),
                List.of(TokenType.MINUS, "Integer", "Integer")
        );

        Type rightType = expr.right.accept(this);
        String resultTypeName = typeMatcher.findMatch(
                typeMap, List.of(expr.operator.type, rightType.getName())
        );
        Type resultType = componentResolver.resolveType(resultTypeName);

        return remember(expr, resultType);
    }

    @Override
    public Type visitBinary(Expr.Binary expr) {
        List<List<Object>> typeMap = List.of(
                List.of(TokenType.EQUAL_EQUAL, TypeMatcher.ANY, TypeMatcher.ANY, "Boolean"),
                List.of(TokenType.BANG_EQUAL, TypeMatcher.ANY, TypeMatcher.ANY, "Boolean"),
                List.of(TokenType.GREATER, "Real", "Real", "Boolean"),
                List.of(TokenType.GREATER, "Integer", "Integer", "Boolean"),
                List.of(TokenType.GREATER, "Integer", "Real", "Boolean"),
                List.of(TokenType.GREATER, "Real", "Integer", "Boolean"),
                List.of(TokenType.LESS, "Real", "Real", "Boolean"),
                List.of(TokenType.LESS, "Integer", "Integer", "Boolean"),
                List.of(TokenType.LESS, "Integer", "Real", "Boolean"),
                List.of(TokenType.LESS, "Real", "Integer", "Boolean"),
                List.of(TokenType.GREATER_EQUAL, "Real", "Real", "Boolean"),
                List.of(TokenType.GREATER_EQUAL, "Integer", "Integer", "Boolean"),
                List.of(TokenType.GREATER_EQUAL, "Integer", "Real", "Boolean"),
                List.of(TokenType.GREATER_EQUAL, "Real", "Integer", "Boolean"),
                List.of(TokenType.LESS_EQUAL, "Real", "Real", "Boolean"),
                List.of(TokenType.LESS_EQUAL, "Integer", "Integer", "Boolean"),
                List.of(TokenType.LESS_EQUAL, "Integer", "Real", "Boolean"),
                List.of(TokenType.LESS_EQUAL, "Real", "Integer", "Boolean"),
                List.of(TokenType.PLUS, "PositiveReal", "PositiveReal", "PositiveReal"),
                List.of(TokenType.PLUS, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                List.of(TokenType.PLUS, "Real", "Real", "Real"),
                List.of(TokenType.PLUS, "Integer", "Integer", "Integer"),
                List.of(TokenType.PLUS, "Integer", "Real", "Real"),
                List.of(TokenType.PLUS, "Real", "Integer", "Real"),
                List.of(TokenType.PLUS, "String", "String", "String"),
                List.of(TokenType.MINUS, "Real", "Real", "Real"),
                List.of(TokenType.MINUS, "Integer", "Integer", "Integer"),
                List.of(TokenType.MINUS, "Integer", "Real", "Real"),
                List.of(TokenType.MINUS, "Real", "Integer", "Real"),
                List.of(TokenType.STAR, "PositiveReal", "PositiveReal", "PositiveReal"),
                List.of(TokenType.STAR, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                List.of(TokenType.STAR, "Real", "Real", "Real"),
                List.of(TokenType.STAR, "Integer", "Integer", "Integer"),
                List.of(TokenType.STAR, "Integer", "Real", "Real"),
                List.of(TokenType.STAR, "Real", "Integer", "Real"),
                List.of(TokenType.SLASH, "PositiveReal", "PositiveReal", "PositiveReal"),
                List.of(TokenType.SLASH, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                List.of(TokenType.SLASH, "Real", "Real", "Real"),
                List.of(TokenType.SLASH, "Integer", "Integer", "Real"),
                List.of(TokenType.SLASH, "Integer", "Real", "Real"),
                List.of(TokenType.SLASH, "Real", "Integer", "Real")
        );

        Type leftType = expr.left.accept(this);
        Type rightType = expr.right.accept(this);
        String resultTypeName = typeMatcher.findMatch(
                typeMap, List.of(expr.operator.type, leftType.getName(), rightType.getName())
        );
        Type resultType = componentResolver.resolveType(resultTypeName);

        return remember(expr, resultType);
    }

    @Override
    public Type visitCall(Expr.Call expr) {
        Type functionType = expr.function.accept(this);
        String functionName = functionType.getName();

        // TODO: right now, methods are not supported (they won't be known generators)
        Generator generator = componentResolver.resolveGenerator(functionName);
        if (generator == null) {
            throw new TypeError("Unknown function: " + functionName);
        }

        // check edge case with only one unnamed argument
        if (expr.arguments.length == 1 && expr.arguments[0].name == null) {
            Type argumentType = expr.arguments[0].accept(this);

            if (generator.getArguments().size() == 1) {
                // the one provided argument belongs to the one possible argument
                assertCompatibility(
                        componentResolver.resolveType(generator.getArguments().getFirst().getType()),
                        argumentType
                );
            }

            // check if there is only one required argument
            Argument requiredArgument = null;
            for (Argument functionArg : generator.getArguments()) {
                if (functionArg.getRequired()) {
                    if (requiredArgument == null) {
                        requiredArgument = functionArg;
                    } else {
                        throw new TypeError("Function " + functionName + " requires more than one argument");
                    }
                }
            }

            if (requiredArgument == null) {
                // there is more than one possible arguments, but none of them are required
                throw new TypeError("Function " + functionName + " does not require any arguments");
            } else {
                assertCompatibility(
                        componentResolver.resolveType(requiredArgument.getType()),
                        argumentType
                );
            }

            Type resultType = componentResolver.resolveType(requiredArgument.getType());
            return remember(expr, resultType);
        }

        // check that all required arguments are passed
        for (Argument functionArg : generator.getArguments()) {
            if (!(functionArg.getRequired())) continue;

            boolean isPassed = false;
            for (Expr.Argument passedArg : expr.arguments) {
                if (passedArg.name.equals(functionArg.getName())) {
                    isPassed = true;
                    break;
                }
            }

            if (!isPassed) {
                throw new TypeError("Function " + functionName + " requires argument " + functionArg.getName());
            }
        }

        // check the types of the passed arguments
        for (Expr.Argument passedArg : expr.arguments) {
            Type passedArgumentType = passedArg.accept(this);

            boolean known = false;
            for (Argument functionArg : generator.getArguments()) {
                if (passedArg.name.equals(functionArg.getName())) {
                    known = true;
                    assertCompatibility(
                            componentResolver.resolveType(functionArg.getType()),
                            passedArgumentType
                    );
                    break;
                }
            }

            if (!known) {
                throw new TypeError("Function " + functionName + " does not take the argument " + passedArg.name);
            }
        }

        return remember(expr, componentResolver.resolveType(generator.getGeneratedType()));
    }

    @Override
    public Type visitAssignedArgument(Expr.AssignedArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Type visitDrawnArgument(Expr.DrawnArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Type visitGrouping(Expr.Grouping expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Type visitArray(Expr.Array expr) {
        throw new TypeError("Arrays are not yet supported");
    }

    @Override
    public Type visitGet(Expr.Get expr) {
        throw new TypeError("Getters are not yet supported");
    }

    @Override
    public Type visitAtomicType(AstType.Atomic expr) {
        // TODO: support generics
        return componentResolver.resolveType(expr.name);
    }

    @Override
    public Type visitGenericType(AstType.Generic expr) {
        throw new TypeError("Generics are not yet supported");
    }

    private void assertCompatibility(Type requiredType, Type givenType) {
        if (givenType == null) {
            throw new IllegalArgumentException("Type does not match required " + requiredType.getName());
        }

        while (!givenType.getName().equals(requiredType.getName())) {
            String looserTypeName = givenType.getExtends();
            if (looserTypeName == null) {
                throw new IllegalArgumentException("Type " + givenType.getName() + " does not match required " + requiredType.getName());
            }
            givenType = componentResolver.resolveType(looserTypeName);
        }
    }

    private Type remember(Expr expr, Type resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        System.out.println("Remember " + expr.accept(printer) + " with " + resolvedType.getName());
        return resolvedType;
    }

    private Type remember(String variableName, Type resolvedType) {
        variableTypes.put(variableName, resolvedType);
        System.out.println("Remember " + variableName + " with " + resolvedType.getName());
        return resolvedType;
    }
}