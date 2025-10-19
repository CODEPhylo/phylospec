package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Property;
import org.phylospec.components.Type;
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

    private final ComponentResolver componentResolver;
    private final TypeMatcher typeMatcher;

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

        Set<ResolvedType> generatedTypeSet = new HashSet<>();

        for (ResolvedType expressionType : resolvedExpressionTypeSet) {
            TypeUtils.visitTypeAndParents(
                    expressionType, x -> {
                        if (x.getName().equals("Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                        }
                        return true; // we still continue
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError("Expression of type " + printType(resolvedExpressionTypeSet) + " is not a distribution");
        }

        if (!TypeUtils.canBeAssignedTo(generatedTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError("Expression of type " + printType(generatedTypeSet) + " cannot be assigned to variable " + stmt.name + " of type " + printType(Set.of(resolvedVariableType)));
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
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "Real", "PositiveReal", "NonNegativeReal");
                yield Set.of("Integer", "Real");
            }
            case Long value -> {
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "Real", "PositiveReal", "NonNegativeReal");
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
                Collectors.toMap(x -> x.name, x -> x.accept(this))
        );

        // fetch all compatible generators
        List<Generator> generators = componentResolver.resolveGenerator(expr.functionName);
        if (generators == null) {
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
        Set<ResolvedType> resolvedTypeSet = expr.expression.accept(this);

        Set<ResolvedType> generatedTypeSet = new HashSet<>();

        for (ResolvedType expressionType : resolvedTypeSet) {
            TypeUtils.visitTypeAndParents(
                    expressionType, x -> {
                        if (x.getName().equals("Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                        }
                        return true; // we still continue
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError("Expression of type " + printType(resolvedTypeSet) + " is not a distribution");
        }

        return remember(expr, generatedTypeSet);
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

        Set<ResolvedType> lcTypeSet = TypeUtils.getLowestCoverTypeSet(elementTypeSets, componentResolver);

        Type vectorComponent = componentResolver.resolveType("Vector");
        Set<ResolvedType> arrayTypeSet = lcTypeSet.stream().map(
                x -> new ResolvedType(vectorComponent, Map.of("T", x))
        ).collect(Collectors.toSet());

        return remember(expr, arrayTypeSet);
    }

    @Override
    public Set<ResolvedType> visitGet(Expr.Get expr) {
        Set<ResolvedType> objectTypeSet = expr.object.accept(this);

        Set<ResolvedType> returnTypeSet = new HashSet<>();
        boolean foundMatchingProperty = false;

        for (ResolvedType objectType : objectTypeSet) {
            Map<String, Property> propertyMap = objectType.getTypeComponent().getProperties().getAdditionalProperties();
            for (Map.Entry<String, Property> propertyEntry : propertyMap.entrySet()) {
                if (propertyEntry.getKey().equals(expr.properyName)) {
                    foundMatchingProperty = true;

                    Map<String, Set<ResolvedType>> map = new HashMap<>();
                    for (Map.Entry<String, ResolvedType> entry : objectType.getParameterTypes().entrySet()) {
                        map.put(entry.getKey(), Set.of(entry.getValue()));
                    }
                    Set<ResolvedType> propertyTypeSet = ResolvedType.fromString(
                            propertyEntry.getValue().getType(), map, componentResolver
                    );
                    returnTypeSet.addAll(propertyTypeSet);

                    break;
                }
            }
        }

        if (!foundMatchingProperty) {
            throw  new TypeError("Unknown property: " + expr.properyName);
        }

        return returnTypeSet;
    }

    @Override
    public ResolvedType visitAtomicType(AstType.Atomic expr) {
        ResolvedType resolvedType = ResolvedType.fromString(expr.name, componentResolver);
        return resolvedType;
    }

    @Override
    public ResolvedType visitGenericType(AstType.Generic expr) {
        ResolvedType resolvedType = ResolvedType.fromString(expr.name, componentResolver);

        if (resolvedType.getParametersNames().size() != expr.typeParameters.length) {
            throw new TypeError("Wrong number of type parameters.");
        }

        for (int i = 0; i < resolvedType.getParametersNames().size(); i++) {
            resolvedType.getParameterTypes().put(
                    resolvedType.getParametersNames().get(i),
                    expr.typeParameters[i].accept(this)
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

    private static String printType(Set<ResolvedType> type) {
        if (type.isEmpty()) {
            return "unknown";
        }
        if (type.size() == 1) {
            return printType(type.iterator().next());
        }
        return "[" + String.join(",", type.stream().map(TypeChecker::printType).toList()) + "]";
    }

    private static String printType(ResolvedType type) {
        String result = type.getName();

        if (type.getParameterTypes().isEmpty()) return result;

        result += "<";

        result += String.join(",", type.getParametersNames().stream().map(x -> printType(type.getParameterTypes().get(x))).toList());

        result += ">";
        return result;
    }
}