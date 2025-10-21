package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Property;
import org.phylospec.components.Type;
import org.phylospec.lexer.TokenType;

import java.util.*;
import java.util.stream.Collectors;

/// This class traverses an AST statement and resolves the types for each
/// AST node and each variable.
///
/// When resolving the types, static type validation is performed. A {@link TypeError}
/// is thrown if a type violation is detected.
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
/// TypeResolver resolver = new TypeResolver(...);
/// statement1.accept(resolver);
/// statement2.accept(resolver);
///
/// Set<ResolvedType> exprType = resolver.resolveType(<some AST expression>);
/// ResolvedType varType = resolver.resolveVariable(<some var name>);
///```
public class TypeResolver implements AstVisitor<ResolvedType, Set<ResolvedType>, ResolvedType> {

    private final ComponentResolver componentResolver;
    private final TypeMatcher typeMatcher;

    public Map<AstNode, Set<ResolvedType>> resolvedTypes;
    public Map<String, ResolvedType> variableTypes;

    AstPrinter printer;

    public TypeResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.typeMatcher = new TypeMatcher(componentResolver);
        this.resolvedTypes = new HashMap<>();
        this.variableTypes = new HashMap<>();
        this.printer = new AstPrinter();
    }

    /**
     * Returns the types associated with the given AST expression.
     * An AST expression can be associated with multiple types (a typeset). Every
     * type corresponds to an interpretation of the AST expression.
     * For instance, the literal `5` can be interpreted as PositiveReal
     * or PositiveInteger. Another example are generators overloaded in
     * their return type.
     */
    public Set<ResolvedType> resolveType(AstNode expression) {
        return this.resolvedTypes.getOrDefault(expression, Set.of());
    }

    /**
     * Returns the types associated with the given AST type node.
     */
    public ResolvedType resolveType(AstType astTypeNode) {
        return this.resolvedTypes.containsKey(astTypeNode) ? this.resolvedTypes.get(astTypeNode).iterator().next() : null;
    }

    /**
     * Returns the types associated with the given AST statement node.
     */
    public ResolvedType resolveType(Stmt astTypeNode) {
        return this.resolvedTypes.containsKey(astTypeNode) ? this.resolvedTypes.get(astTypeNode).iterator().next() : null;
    }

    /**
     * Returns the type associated with the given variable name. The type of the
     * variable is determined by the type specified at assignment (e.g. Real a = ...).
     * Thus, every variable is only associated with a single type.
     */
    public ResolvedType resolveVariable(String variableName) {
        return this.variableTypes.get(variableName);
    }

    /**
     * visitor functions
     */

    @Override
    public ResolvedType visitDecoratedStmt(Stmt.Decorated stmt) {
        return remember(stmt, stmt.statememt.accept(this));
    }

    @Override
    public ResolvedType visitAssignment(Stmt.Assignment stmt) {
        ResolvedType resolvedVariableType = stmt.type.accept(this);
        remember(stmt.name, resolvedVariableType);

        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);

        if (!TypeUtils.canBeAssignedTo(resolvedExpressionTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError(stmt, "Expression of type `" + printType(resolvedExpressionTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(Set.of(resolvedVariableType)) + "`");
        }

        return remember(stmt, resolvedVariableType);
    }

    @Override
    public ResolvedType visitDraw(Stmt.Draw stmt) {
        ResolvedType resolvedVariableType = stmt.type.accept(this);
        remember(stmt.name, resolvedVariableType);

        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);

        // we are only interested in the expression types which are distributions,
        // because we want to draw a value

        Set<ResolvedType> generatedTypeSet = new HashSet<>();
        for (ResolvedType expressionType : resolvedExpressionTypeSet) {
            TypeUtils.visitTypeAndParents(
                    expressionType, x -> {
                        if (x.getName().equals("Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                        }
                        return TypeUtils.Visitor.CONTINUE;
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(stmt, "Expression of type `" + printType(resolvedExpressionTypeSet) + "` is not a distribution. Do you want to assign it using `=` instead of `~`?");
        }

        if (!TypeUtils.canBeAssignedTo(generatedTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError(stmt, "Expression of type `" + printType(generatedTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(Set.of(resolvedVariableType)) + "`");
        }

        return remember(stmt, resolvedVariableType);
    }

    @Override
    public ResolvedType visitImport(Stmt.Import stmt) {
        componentResolver.importNamespace(stmt.namespace);
        return null;
    }

    @Override
    public Set<ResolvedType> visitLiteral(Expr.Literal expr) {
        // TODO: only specify the most specific type. this does not work atm due to a bug in TypeMatcher
        Set<String> typeName = switch (expr.value) {
            case String ignored -> Set.of("String");
            case Integer value -> {
                if (0 == value) yield Set.of("Integer", "NonNegativeReal", "Real", "Probability");
                if (1 == value)
                    yield Set.of("PositiveInteger", "Integer", "NonNegativeReal", "PositiveReal", "Real", "Probability");
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "NonNegativeReal", "PositiveReal", "Real");
                yield Set.of("Integer", "Real");
            }
            case Long value -> {
                if (0 == value) yield Set.of("Integer", "NonNegativeReal", "Real", "Probability");
                if (1 == value)
                    yield Set.of("PositiveInteger", "Integer", "NonNegativeReal", "PositiveReal", "Real", "Probability");
                if (0 < value) yield Set.of("PositiveInteger", "Integer", "NonNegativeReal", "PositiveReal", "Real");
                yield Set.of("Integer", "Real");
            }
            case Float value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Real", "Probability");
                if (0 <= value && value <= 1) yield Set.of("Probability", "PositiveReal", "NonNegativeReal", "Real");
                if (0 < value) yield Set.of("PositiveReal", "NonNegativeReal", "Real");
                yield Set.of("Real");
            }
            case Double value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Real", "Probability");
                if (0 <= value && value <= 1) yield Set.of("Probability", "PositiveReal", "NonNegativeReal", "Real");
                if (0 < value) yield Set.of("PositiveReal", "NonNegativeReal", "Real");
                yield Set.of("Real");
            }
            case Boolean value -> Set.of("Boolean");
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
            throw new TypeError(expr, "Variable `" + variableName + "` is not known");
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
                typeMap, new TypeMatcher.Query(expr.operator, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError(expr, "Operation `" + TokenType.getLexeme(expr.operator) + "` is not supported for type `" + rightType + "`");
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
                typeMap, new TypeMatcher.Query(expr.operator, leftType, rightType)
        );

        if (resultType.isEmpty()) {
            throw new TypeError(expr, "Operation `" + TokenType.getLexeme(expr.operator) + "` is not supported for types `" + printType(leftType) + "` and `" + printType(rightType) + "`");
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
        if (generators.isEmpty()) {
            throw new TypeError(expr, "Function `" + expr.functionName + "` is not known");
        }

        // check if generators are compatible with arguments
        Set<ResolvedType> possibleReturnTypes = new HashSet<>();
        List<String> errorMessages = new ArrayList<>();
        for (Generator generator : generators) {
            try {
                Set<ResolvedType> possibleGeneratorReturnTypes = TypeUtils.resolveGeneratedType(
                        generator, resolvedArguments, componentResolver
                );
                possibleReturnTypes.addAll(possibleGeneratorReturnTypes);
            } catch (TypeError e) {
                errorMessages.add(e.getMessage());
            }
        }

        // throw errors if needed
        if (possibleReturnTypes.isEmpty() && errorMessages.isEmpty()) {
            throw new TypeError(expr, "Function `" + expr.functionName + "` with the given arguments is not known");
        } else if (possibleReturnTypes.isEmpty() && errorMessages.size() == 1) {
            throw new TypeError(expr, errorMessages.getFirst());
        } else if (possibleReturnTypes.isEmpty()) {
            String errorMessage = "Function `" + expr.functionName + "` with the given arguments is not known: \n\t";
            errorMessage += String.join("\n\t", errorMessages);
            throw new TypeError(expr, errorMessage);
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

        // we only consider Distribution types, because we want to draw the argument value

        Set<ResolvedType> generatedTypeSet = new HashSet<>();
        for (ResolvedType expressionType : resolvedTypeSet) {
            TypeUtils.visitTypeAndParents(
                    expressionType, x -> {
                        if (x.getName().equals("Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                        }
                        return TypeUtils.Visitor.CONTINUE;
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(expr, "Expression of type `" + printType(resolvedTypeSet) + "` is not a distribution. Do you want to assign it using `=` instead of `~`?");
        }

        return remember(expr, generatedTypeSet);
    }

    @Override
    public Set<ResolvedType> visitGrouping(Expr.Grouping expr) {
        return remember(expr, expr.expression.accept(this));
    }

    @Override
    public Set<ResolvedType> visitArray(Expr.Array expr) {
        // resolve the element types

        List<Set<ResolvedType>> elementTypeSets = expr.elements.stream()
                .map(x -> x.accept(this))
                .collect(Collectors.toList());

        // get the most specific type compatible with the element types
        // this is done by looking at the product of the typesets for every
        // single element. for each possible type combination, the lowest
        // cover is determined (the most specific supertype)

        Set<ResolvedType> lcTypeSet = TypeUtils.getLowestCoverTypeSet(elementTypeSets, componentResolver);

        // build the Vector result type

        Type vectorComponent = componentResolver.resolveType("Vector");
        Set<ResolvedType> arrayTypeSet = lcTypeSet.stream().map(
                x -> new ResolvedType(vectorComponent, Map.of("T", x))
        ).collect(Collectors.toSet());

        return remember(expr, arrayTypeSet);
    }

    @Override
    public Set<ResolvedType> visitGet(Expr.Get expr) {
        Set<ResolvedType> objectTypeSet = expr.object.accept(this);

        // we have to look at all possible object types, check if they have the
        // correct method, and collect the corresponding return types

        Set<ResolvedType> returnTypeSet = new HashSet<>();
        boolean foundMatchingProperty = false;

        for (ResolvedType objectType : objectTypeSet) {
            Map<String, Property> propertyMap = objectType.getTypeComponent().getProperties().getAdditionalProperties();
            for (Map.Entry<String, Property> propertyEntry : propertyMap.entrySet()) {
                if (!propertyEntry.getKey().equals(expr.properyName)) continue;
                foundMatchingProperty = true;

                // we fetch the return type of this parameter while taking into account
                // the generic type parameters

                // some hacky conversion from Map<String, ResolvedType> to Map<String, Set<ResolvedType>>
                // to adhere to types
                // TODO: this is very hacky rn, look if we can improve this
                Map<String, Set<ResolvedType>> typeParameterTypeSets = new HashMap<>();
                for (Map.Entry<String, ResolvedType> entry : objectType.getParameterTypes().entrySet()) {
                    typeParameterTypeSets.put(entry.getKey(), Set.of(entry.getValue()));
                }

                Set<ResolvedType> propertyTypeSet = ResolvedType.fromString(
                        propertyEntry.getValue().getType(), typeParameterTypeSets, componentResolver
                );
                returnTypeSet.addAll(propertyTypeSet);

                break;
            }
        }

        if (!foundMatchingProperty) {
            throw new TypeError(expr, "Property `" + expr.properyName + "` is not known");
        }

        return remember(expr, returnTypeSet);
    }

    @Override
    public ResolvedType visitAtomicType(AstType.Atomic expr) {
        try {
            return remember(expr, ResolvedType.fromString(expr.name, componentResolver));
        } catch (TypeError error) {
            throw new TypeError(expr, error.getMessage());
        }
    }

    @Override
    public ResolvedType visitGenericType(AstType.Generic expr) {
        ResolvedType resolvedType = ResolvedType.fromString(expr.name, componentResolver);

        if (resolvedType.getParametersNames().size() != expr.typeParameters.length) {
            throw new TypeError(expr, "Type `" + expr.name + "` takes " + resolvedType.getParametersNames().size() + " type parameters");
        }

        // resolve the type parameters
        for (int i = 0; i < resolvedType.getParametersNames().size(); i++) {
            resolvedType.getParameterTypes().put(
                    resolvedType.getParametersNames().get(i),
                    expr.typeParameters[i].accept(this)
            );
        }

        return remember(expr, resolvedType);
    }

    /**
     * helper functions to store the resolved types
     */

    private ResolvedType remember(Stmt expr, ResolvedType resolvedType) {
        resolvedTypes.put(expr, Set.of(resolvedType));
        return resolvedType;
    }

    private Set<ResolvedType> remember(Expr expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        System.out.println("Remember " + expr.accept(printer) + " with " + printType(resolvedType));
        return resolvedType;
    }

    private ResolvedType remember(AstType expr, ResolvedType resolvedType) {
        resolvedTypes.put(expr, Set.of(resolvedType));
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

    /**
     * helper functions to pretty-print types
     */

    private static String printType(Set<ResolvedType> type) {
        if (type.isEmpty()) {
            return "unknown";
        }
        if (type.size() == 1) {
            return printType(type.iterator().next());
        }
        return "[" + String.join(",", type.stream().map(TypeResolver::printType).toList()) + "]";
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