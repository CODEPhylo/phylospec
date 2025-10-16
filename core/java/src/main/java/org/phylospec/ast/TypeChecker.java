package org.phylospec.ast;

import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Type;
import org.phylospec.lexer.TokenType;

import java.util.*;

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
public class TypeChecker implements AstVisitor<Void, Set<Type>, Type> {

    private ComponentResolver componentResolver;
    private TypeMatcher typeMatcher;

    Map<Expr, Set<Type>> resolvedTypes;
    Map<String, Type> variableTypes;

    AstPrinter printer;

    public TypeChecker(ComponentResolver componentResolver) {
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
        Set<Type> resolvedExpressionType = stmt.expression.accept(this);
        Type resolvedVariableType = stmt.type.accept(this);

        if (!checkCompatibility(resolvedVariableType, resolvedExpressionType)) {
            throw new TypeError("Expression of type " + printType(resolvedExpressionType) + " cannot be assigned to variable " + stmt.name + " of type " + resolvedVariableType.getName());
        };

        remember(stmt.name, resolvedVariableType);
        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        Set<Type> resolvedExpressionType = stmt.expression.accept(this);
        Type resolvedVariableType = stmt.type.accept(this);

        if (!checkCompatibility(resolvedVariableType, resolvedExpressionType)) {
            throw new TypeError("Expression of type " + printType(resolvedExpressionType) + " cannot be assigned to variable " + stmt.name + " of type " + resolvedVariableType.getName());
        };

        remember(stmt.name, resolvedVariableType);
        return null;
    }

    @Override
    public Void visitImport(Stmt.Import stmt) {
        componentResolver.importNamespace(stmt.namespace);
        return null;
    }

    @Override
    public Set<Type> visitLiteral(Expr.Literal expr) {
        Set<String> typeName = switch (expr.value) {
            case String ignored -> Set.of("String");
            case Integer ignored -> Set.of("Integer", "Real");
            case Long ignored -> Set.of("Integer", "Real");
            case Float value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Real");
                if (0 < value) yield Set.of("PositiveReal", "NonNegativeReal", "Real");
                yield Set.of("Real");
            }
            case Double value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Real");
                if (0 < value) yield Set.of("PositiveReal", "NonNegativeReal", "Real");
                yield Set.of("Real");
            }
            default -> Set.of();
        };
        Set<Type> resolvedType = new HashSet<>(
                typeName.stream().map(componentResolver::resolveType).toList()
        );

        return remember(expr, resolvedType);
    }

    @Override
    public Set<Type> visitVariable(Expr.Variable expr) {
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

        return remember(expr, Set.of(resolvedType));
    }

    @Override
    public Set<Type> visitUnary(Expr.Unary expr) {
        List<TypeMatcher.Rule> typeMap = List.of(
                new TypeMatcher.Rule(TokenType.BANG, "Boolean", "Boolean"),
                new TypeMatcher.Rule(TokenType.MINUS, "Real", "Real"),
                new TypeMatcher.Rule(TokenType.MINUS, "Integer", "Integer")
        );

        Set<Type> rightType = expr.right.accept(this);
        Set<Type> resultType = typeMatcher.findMatch(
                typeMap, new TypeMatcher.Query(expr.operator.type, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError("Operation " + expr.operator.lexeme + " is not supported for type " + rightType);
        }

        return remember(expr, resultType);
    }

    @Override
    public Set<Type> visitBinary(Expr.Binary expr) {
        List<TypeMatcher.Rule> typeMap = List.of(
                new TypeMatcher.Rule(TokenType.EQUAL_EQUAL, TypeMatcher.ANY, TypeMatcher.ANY, "Boolean"),
                new TypeMatcher.Rule(TokenType.BANG_EQUAL, TypeMatcher.ANY, TypeMatcher.ANY, "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER, "Real", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER, "Integer", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER, "Integer", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER, "Real", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS, "Real", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS, "Integer", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS, "Integer", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS, "Real", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER_EQUAL, "Real", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER_EQUAL, "Integer", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER_EQUAL, "Integer", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.GREATER_EQUAL, "Real", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS_EQUAL, "Real", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS_EQUAL, "Integer", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS_EQUAL, "Integer", "Real", "Boolean"),
                new TypeMatcher.Rule(TokenType.LESS_EQUAL, "Real", "Integer", "Boolean"),
                new TypeMatcher.Rule(TokenType.PLUS, "PositiveReal", "PositiveReal", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.PLUS, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                new TypeMatcher.Rule(TokenType.PLUS, "Real", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.PLUS, "Integer", "Integer", "Integer"),
                new TypeMatcher.Rule(TokenType.PLUS, "Integer", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.PLUS, "Real", "Integer", "Real"),
                new TypeMatcher.Rule(TokenType.PLUS, "String", "String", "String"),
                new TypeMatcher.Rule(TokenType.MINUS, "Real", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.MINUS, "Integer", "Integer", "Integer"),
                new TypeMatcher.Rule(TokenType.MINUS, "Integer", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.MINUS, "Real", "Integer", "Real"),
                new TypeMatcher.Rule(TokenType.STAR, "PositiveReal", "PositiveReal", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.STAR, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                new TypeMatcher.Rule(TokenType.STAR, "Real", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.STAR, "Integer", "Integer", "Integer"),
                new TypeMatcher.Rule(TokenType.STAR, "Integer", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.STAR, "Real", "Integer", "Real"),
                new TypeMatcher.Rule(TokenType.SLASH, "PositiveReal", "PositiveReal", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.SLASH, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                new TypeMatcher.Rule(TokenType.SLASH, "Real", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.SLASH, "Integer", "Integer", "Real"),
                new TypeMatcher.Rule(TokenType.SLASH, "Integer", "Real", "Real"),
                new TypeMatcher.Rule(TokenType.SLASH, "Real", "Integer", "Real")
        );

        Set<Type> leftType = expr.left.accept(this);
        Set<Type> rightType = expr.right.accept(this);
        Set<Type> resultType = typeMatcher.findMatch(
                typeMap, new TypeMatcher.Query(expr.operator.type, leftType, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError("Operation " + expr.operator.lexeme + " is not supported for types " + printType(leftType) + " and " + printType(rightType));
        }

        return remember(expr, resultType);
    }

    @Override
    public Set<Type> visitCall(Expr.Call expr) {
        Set<Type> functionType = expr.function.accept(this);

        // TODO: right now, methods are not supported (they won't be known generators)
        List<Generator> generators = new ArrayList<>();
        for (Type type : functionType) {
            generators.addAll(componentResolver.resolveGenerator(type.getName()));
        }
        if (generators == null || generators.isEmpty()) {
            throw new TypeError("Unknown function: " + expr.function.accept(printer));
        }

        List<Set<Type>> resolvedPassedValueTypes = Arrays.stream(
                expr.arguments).map(x -> x.expression.accept(this)
        ).toList();

        Set<String> possibleReturnTypeNames = new HashSet<>();

        generatorloop:
        for (Generator generator : generators) {
            // check edge case with only one unnamed argument
            if (expr.arguments.length == 1 && expr.arguments[0].name == null) {
                Set<Type> argumentType = resolvedPassedValueTypes.get(0);

                if (generator.getArguments().size() == 1) {
                    // the one provided argument belongs to the one possible argument
                    if (checkCompatibility(
                            componentResolver.resolveType(generator.getArguments().getFirst().getType()),
                            argumentType
                    )) {
                        possibleReturnTypeNames.add(generator.getGeneratedType());
                    };
                }

                // check if there is only one required argument
                Argument requiredArgument = null;
                for (Argument functionArg : generator.getArguments()) {
                    if (functionArg.getRequired()) {
                        if (requiredArgument == null) {
                            requiredArgument = functionArg;
                        } else {
                            // this generator requires more than one argument
                            continue generatorloop;
                        }
                    }
                }

                if (requiredArgument == null) {
                    // there is more than one possible arguments, but none of them are required
                    continue generatorloop;
                } else {
                    if (checkCompatibility(
                            componentResolver.resolveType(requiredArgument.getType()),
                            argumentType
                    )) {
                        possibleReturnTypeNames.add(generator.getGeneratedType());
                    }
                }

                continue generatorloop;
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
                    // argument is not passed
                    continue generatorloop;
                }
            }

            // check the types of the passed arguments
            for (Expr.Argument passedArg : expr.arguments) {
                Set<Type> passedArgumentType = passedArg.accept(this);

                boolean known = false;
                for (Argument functionArg : generator.getArguments()) {
                    if (passedArg.name.equals(functionArg.getName())) {
                        known = true;

                        if (!checkCompatibility(
                                componentResolver.resolveType(functionArg.getType()),
                                passedArgumentType
                        )) {
                            // types are not compatible
                            continue generatorloop;
                        }

                        break;
                    }
                }

                if (!known) {
                    // unknown argument passed
                    continue generatorloop;
                }
            }

            possibleReturnTypeNames.add(generator.getGeneratedType());
        }

        if (possibleReturnTypeNames.isEmpty()) {
            throw new TypeError("Function with the given arguments is not known: " + expr.function.accept(printer));
        }

        Set<Type> possibleReturnTypes = new HashSet<>();
        for (String possibleReturnTypeName : possibleReturnTypeNames) {
            Type possibleReturnType = componentResolver.resolveType(possibleReturnTypeName);
            if  (possibleReturnType == null) {
                throw new TypeError("Possible return type not imported: " + possibleReturnTypeName);
            }
            possibleReturnTypes.add(possibleReturnType);
        }

        return remember(expr, possibleReturnTypes);
    }

    @Override
    public Set<Type> visitAssignedArgument(Expr.AssignedArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<Type> visitDrawnArgument(Expr.DrawnArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<Type> visitGrouping(Expr.Grouping expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<Type> visitArray(Expr.Array expr) {
        throw new TypeError("Arrays are not yet supported");
    }

    @Override
    public Set<Type> visitGet(Expr.Get expr) {
        throw new TypeError("Getters are not yet supported");
    }

    @Override
    public Type visitAtomicType(AstType.Atomic expr) {
        // TODO: support generics
        Type resolvedType = componentResolver.resolveType(expr.name);

        if (resolvedType == null) {
            throw new TypeError("Unknown type: " + expr.name);
        }

        return resolvedType;
    }

    @Override
    public Type visitGenericType(AstType.Generic expr) {
        throw new TypeError("Generics are not yet supported");
    }

    /** Checks that the given type or any of its widened types matches
     * the required type. */
    private boolean checkCompatibility(Type requiredType, Set<Type> givenType) {
        assert (requiredType != null);

        if (givenType.isEmpty()) {
            return false;
        }

        for (Type type : givenType) {
            boolean compatibleTypeFound = true;
            while (!type.getName().equals(requiredType.getName())) {
                String looserTypeName = type.getExtends();
                if (looserTypeName == null) {
                    compatibleTypeFound = false;
                    break;
                }
                type = componentResolver.resolveType(looserTypeName);

                if (type == null) {
                    compatibleTypeFound = false;
                    break;
                }
            }

            if (compatibleTypeFound) return true;
        }

        return false;
    }

    private Set<Type> remember(Expr expr, Set<Type> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        System.out.println("Remember " + expr.accept(printer) + " with " + printType(resolvedType));
        return resolvedType;
    }

    private Type remember(String variableName, Type resolvedType) {
        variableTypes.put(variableName, resolvedType);
        if (resolvedType != null) {
            System.out.println("Remember " + variableName + " with " + resolvedType.getName());
        } else {
            System.out.println("Remember " + variableName + " with unknown type");
        }
        return resolvedType;
    }
    private String printType(Set<Type> type) {
        if (type.isEmpty()) {
            return "unknown";
        }
        if (type.size() == 1) {
            return type.iterator().next().getName();
        }
        return "[" + String.join(",", type.stream().map(Type::getName).toList()) + "]";
    }
}