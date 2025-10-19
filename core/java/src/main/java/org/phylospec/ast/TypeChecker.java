package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.lexer.TokenType;

import java.util.*;
import java.util.stream.Collectors;

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
public class TypeChecker implements AstVisitor<Void, Set<ResolvedType>, ResolvedType> {

    private ComponentResolver componentResolver;
    private TypeMatcher typeMatcher;

    Map<Expr, Set<ResolvedType>> resolvedTypes;
    Map<String, ResolvedType> variableTypes;

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
        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);
        ResolvedType resolvedVariableType = stmt.type.accept(this);

        if (!TypeUtils.canBeAssignedTo(resolvedExpressionTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError("Expression of type " + printType(resolvedExpressionTypeSet) + " cannot be assigned to variable " + stmt.name + " of type " + printType(Set.of(resolvedVariableType)));
        };

        remember(stmt.name, resolvedVariableType);
        return null;
    }

    @Override
    public Void visitDraw(Stmt.Draw stmt) {
        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);
        ResolvedType resolvedVariableType = stmt.type.accept(this);

        if (!TypeUtils.canBeAssignedTo(resolvedExpressionTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError("Expression of type " + printType(resolvedExpressionTypeSet) + " cannot be assigned to variable " + stmt.name + " of type " + printType(Set.of(resolvedVariableType)));
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
    public Set<ResolvedType> visitLiteral(Expr.Literal expr) {
        Set<String> typeName = switch (expr.value) {
            case String ignored -> Set.of("String");
            case Integer value -> {
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "Real");
                yield Set.of("Integer", "Real");
            }
            case Long value -> {
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "Real");
                yield Set.of("Integer", "Real");
            }
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
        Set<ResolvedType> resolvedType = typeName.stream()
                .map(x -> ResolvedType.fromString(x, componentResolver))
                .collect(Collectors.toSet());

        return remember(expr, resolvedType);
    }

    @Override
    public Set<ResolvedType> visitVariable(Expr.Variable expr) {
        String variableName = expr.variableName;

        ResolvedType resolvedType = variableTypes.get(variableName);

        if (resolvedType == null) {
            throw new TypeError("Unknown variable: " + variableName);
        }

        return remember(expr, Set.of(resolvedType));
    }

    @Override
    public Set<ResolvedType> visitUnary(Expr.Unary expr) {
        List<TypeMatcher.Rule> typeMap = List.of(
                new TypeMatcher.Rule(TokenType.BANG, "Boolean", "Boolean"),
                new TypeMatcher.Rule(TokenType.MINUS, "Real", "Real"),
                new TypeMatcher.Rule(TokenType.MINUS, "Integer", "Integer")
        );

        Set<ResolvedType> rightType = expr.right.accept(this);
        Set<ResolvedType> resultType = typeMatcher.findMatch(
                typeMap, new TypeMatcher.Query(expr.operator.type, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError("Operation " + expr.operator.lexeme + " is not supported for type " + rightType);
        }

        return remember(expr, resultType);
    }

    @Override
    public Set<ResolvedType> visitBinary(Expr.Binary expr) {
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

        Set<ResolvedType> leftType = expr.left.accept(this);
        Set<ResolvedType> rightType = expr.right.accept(this);
        Set<ResolvedType> resultType = typeMatcher.findMatch(
                typeMap, new TypeMatcher.Query(expr.operator.type, leftType, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError("Operation " + expr.operator.lexeme + " is not supported for types " + printType(leftType) + " and " + printType(rightType));
        }

        return remember(expr, resultType);
    }

    @Override
    public Set<ResolvedType> visitCall(Expr.Call expr) {
         // resolve arguments
        Map<String, Set<ResolvedType>> resolvedArguments = Arrays.stream(expr.arguments).collect(
                Collectors.toMap(x -> x.name, x -> x.expression.accept(this))
        );

        // fetch all compatible generators
        // TODO: right now, methods are not supported (they won't be known generators)
        List<Generator> generators = componentResolver.resolveGenerator(expr.functionName);
        if (generators.isEmpty()) {
            throw new TypeError("Unknown function: " + expr.functionName);
        }

        // check if generators are compatible with arguments
        Set<ResolvedType> possibleReturnTypes = new HashSet<>();
        List<String> errorMessages = new ArrayList<>();
        for (Generator generator : generators) {
            try {
                possibleReturnTypes.addAll(TypeUtils.resolveGeneratedType(
                        generator, resolvedArguments, componentResolver
                ));
            }  catch (TypeError e) {
                errorMessages.add(e.getMessage());
            }
        }

        if (possibleReturnTypes.isEmpty() && errorMessages.isEmpty()) {
            throw new TypeError("Function with the given arguments is not known: " + expr.functionName);
        } else if (possibleReturnTypes.isEmpty()) {
            String errorMessage = "Function with the given arguments is not known: ";
            errorMessage += String.join("\n", errorMessages);
            throw new TypeError(errorMessage);
        }

        return remember(expr, possibleReturnTypes);
    }

    @Override
    public Set<ResolvedType> visitAssignedArgument(Expr.AssignedArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<ResolvedType> visitDrawnArgument(Expr.DrawnArgument expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<ResolvedType> visitGrouping(Expr.Grouping expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<ResolvedType> visitArray(Expr.Array expr) {
        List<Set<ResolvedType>> elementTypeSets = expr.elements.stream()
                .map(x -> x.accept(this))
                .collect(Collectors.toList());

        Set<ResolvedType> arrayType = TypeUtils.inferArrayType(elementTypeSets, componentResolver);

        return remember(expr, arrayType);
    }

    @Override
    public Set<ResolvedType> visitGet(Expr.Get expr) {
        throw new TypeError("Getters are not yet supported");
    }

    @Override
    public ResolvedType visitAtomicType(AstType.Atomic expr) {
        ResolvedType resolvedType = ResolvedType.fromString(expr.name, componentResolver);
        return resolvedType;
    }

    @Override
    public ResolvedType visitGenericType(AstType.Generic expr) {
        ResolvedType resolvedType = ResolvedType.fromString(expr.name, componentResolver);

        for (AstType typeParam : expr.typeParameters) {
            resolvedType.getParameterTypes().add(
                    typeParam.accept(this)
            );
        }

        return resolvedType;
    }

    private Set<ResolvedType> remember(Expr expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        System.out.println("Remember " + expr.accept(printer) + " with " + printType(resolvedType));
        return resolvedType;
    }

    private ResolvedType remember(String variableName, ResolvedType resolvedType) {
        variableTypes.put(variableName, resolvedType);
        if (resolvedType != null) {
            System.out.println("Remember " + variableName + " with " + printType(Set.of(resolvedType)));
        } else {
            System.out.println("Remember " + variableName + " with unknown type");
        }
        return resolvedType;
    }

    private String printType(Set<ResolvedType> type) {
        if (type.isEmpty()) {
            return "unknown";
        }
        if (type.size() == 1) {
            ResolvedType onlyType = type.iterator().next();
            return onlyType.getName() +  "<" + String.join(",", onlyType.getParameterTypes().stream().map(ResolvedType::getName).toList()) + ">";
        }
        return "[" + String.join(",", type.stream().map(x -> x.getName() + "<" + String.join(",", x.getParameterTypes().stream().map(ResolvedType::getName).toList()) + ">").toList()) + "]";
    }
}