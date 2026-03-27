package org.phylospec.typeresolver;

import org.phylospec.Utils;
import org.phylospec.ast.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
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
    private final List<Map<String, Set<ResolvedType>>> scopedVariableTypes;

    AstPrinter printer;

    public TypeResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.typeMatcher = new TypeMatcher(componentResolver);
        this.resolvedTypes = new HashMap<>();
        this.scopedVariableTypes = new ArrayList<>();
        this.printer = new AstPrinter();

        createScope();
    }

    /**
     * Returns the types associated with the given AST expression. Returns an empty
     * set if no type is known.
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
     * Returns the types associated with the given AST type node. Returns null if no
     * type is known.
     */
    public ResolvedType resolveType(AstType astTypeNode) {
        return this.resolvedTypes.containsKey(astTypeNode) ? this.resolvedTypes.get(astTypeNode).iterator().next() : null;
    }

    /**
     * Returns the types associated with the given AST statement node. Returns null if
     * no type is known.
     */
    public ResolvedType resolveType(Stmt astTypeNode) {
        return this.resolvedTypes.containsKey(astTypeNode) ? this.resolvedTypes.get(astTypeNode).iterator().next() : null;
    }

    /**
     * Returns the type associated with the given variable name. The type of the
     * variable is determined by the type specified at assignment (e.g. Real a = ...).
     * Thus, every variable is only associated with a single type.
     * Returns an empty set if no type is known.
     */
    public Set<ResolvedType> resolveVariable(String variableName) {
        // we go through all nested scopes to check if we find the variable there
        for (Map<String, Set<ResolvedType>> scope : scopedVariableTypes) {
            if (scope.containsKey(variableName)) {
                return scope.get(variableName);
            }
        }

        // we don't know this variable
        return Set.of();
    }

    /**
     * Return a set of the global variable names of this model.
     */
    public Set<String> getVariableNames() {
        return this.scopedVariableTypes.getLast().keySet();
    }

    /**
     * visitor functions
     */

    @Override
    public ResolvedType visitDecoratedStmt(Stmt.Decorated stmt) {
        return remember(stmt, stmt.statement.accept(this));
    }

    @Override
    public ResolvedType visitAssignment(Stmt.Assignment stmt) {
        ResolvedType resolvedVariableType = stmt.type.accept(this);
        remember(stmt.name, Set.of(resolvedVariableType));

        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);

        if (!TypeUtils.canBeAssignedTo(resolvedExpressionTypeSet, resolvedVariableType, componentResolver)) {
            // TODO: check if distribution and give hint to use ~
            throw new TypeError(
                    stmt,
                    "Expression of type `" + printType(resolvedExpressionTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(Set.of(resolvedVariableType)) + "`.",
                    "Change the type of the variable to `" + printType(resolvedExpressionTypeSet) + "`, or change what you assign to it."
            );
        }

        return remember(stmt, resolvedVariableType);
    }

    @Override
    public ResolvedType visitDraw(Stmt.Draw stmt) {
        ResolvedType resolvedVariableType = stmt.type.accept(this);
        remember(stmt.name, Set.of(resolvedVariableType));

        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);

        // we are only interested in the expression types which are distributions,
        // because we want to draw a value

        Set<ResolvedType> generatedTypeSet = new HashSet<>();
        for (ResolvedType expressionType : resolvedExpressionTypeSet) {
            TypeUtils.visitTypeAndParents(
                    expressionType, x -> {
                        if (x.getName().equals("phylospec.types.Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                        }
                        return TypeUtils.Visitor.CONTINUE;
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(
                    stmt,
                    "Expression after `~' is not a distribution.",
                    "After '~', you always need to provide a distribution. Do you want use `=` instead?"
            );
        }

        if (!TypeUtils.canBeAssignedTo(generatedTypeSet, resolvedVariableType, componentResolver)) {
            throw new TypeError(
                    stmt,
                    "Expression of type `" + printType(generatedTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(Set.of(resolvedVariableType)) + "`.",
                    "Change the type of the variable to `" + printType(resolvedExpressionTypeSet) + "`, or change what value you draw and assign to it."
            );
        }

        return remember(stmt, resolvedVariableType);
    }

    @Override
    public ResolvedType visitImport(Stmt.Import stmt) {
        try {
            componentResolver.importNamespace(stmt.namespace);
        } catch (TypeError e) {
            throw e.attachAstNode(stmt);
        }
        return null;
    }

    @Override
    public ResolvedType visitIndexedStmt(Stmt.Indexed indexed) {
        if (indexed.indices.size() != indexed.ranges.size()) {
            throw new TypeError(
                    indexed,
                    "Number of index variables does not match number of ranges.",
                    "Provide one range for each index variable."
            );
        }

        if (indexed.indices.isEmpty() || 2 < indexed.indices.size()) {
            throw new TypeError(
                    indexed,
                    "Indexed statements must have one or two indices.",
                    "Use one index for vectors and two indices for matrices."
            );
        }

        // evaluate each range and add the corresponding index variable to a new scope

        createScope();

        ResolvedType innerType;
        try {
            for (int i = 0; i < indexed.indices.size(); i++) {
                Expr.Variable indexVar = indexed.indices.get(i);
                Set<ResolvedType> rangeTypeSet = indexed.ranges.get(i).accept(this);

                if (!TypeUtils.canBeAssignedTo(rangeTypeSet, ResolvedType.fromString("Vector<NonNegativeInteger>", componentResolver), componentResolver)) {
                    throw new TypeError(
                            indexed,
                            "Index range must produce positive integer values.",
                            "Use integer expressions as the range bounds (e.g., '1:10')."
                    );
                }

                Set<ResolvedType> indexVarTypeSet = Set.of(ResolvedType.fromString("NonNegativeInteger", componentResolver));
                remember(indexVar.variableName, indexVarTypeSet);
            }

            // evaluate the inner statement with the index variables in scope

            innerType = indexed.statement.accept(this);
        } finally {
            dropScope();
        }

        if (innerType == null) {
            return null;
        }

        // widen the result type: Vector<T> for one index, Matrix<T> for two

        Set<ResolvedType> widenedType;
        if (indexed.indices.size() == 1) {
            widenedType = ResolvedType.fromString("Vector<T>",  Map.of("T", Set.of(innerType)), componentResolver);
        } else {
            widenedType = ResolvedType.fromString("Matrix<T>",  Map.of("T", Set.of(innerType)), componentResolver);
        }

        // register the widened type in the outer scope under the variable name

        String variableName = extractVariableName(indexed.statement);
        if (variableName != null) {
            remember(variableName, widenedType);
        }

        return remember(indexed, widenedType.iterator().next());
    }

    private String extractVariableName(Stmt stmt) {
        if (stmt instanceof Stmt.Assignment a) return a.name;
        if (stmt instanceof Stmt.Draw d) return d.name;
        if (stmt instanceof Stmt.Decorated decorated) return extractVariableName(decorated.statement);
        return null;
    }

    @Override
    public Set<ResolvedType> visitLiteral(Expr.Literal expr) {
        // TODO: only specify the most specific type. this does not work atm due to a bug in TypeMatcher
        Set<String> typeName = switch (expr.value) {
            case String ignored -> Set.of("String");
            case Integer value -> {
                if (0 == value) yield Set.of("Integer", "NonNegativeReal", "Probability");
                if (1 == value)
                    yield Set.of("PositiveReal", "PositiveInteger", "Probability");
                if (0 < value) yield Set.of("PositiveInteger", "PositiveReal");
                yield Set.of("Integer", "Real");
            }
            case Long value -> {
                if (0 == value) yield Set.of("Integer", "NonNegativeReal", "Probability");
                if (1 == value)
                    yield Set.of("PositiveReal", "PositiveInteger", "Probability");
                if (0 < value) yield Set.of("PositiveInteger", "PositiveReal");
                yield Set.of("Integer", "Real");
            }
            case Float value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Probability");
                if (0 < value && value <= 1) yield Set.of("Probability", "PositiveReal");
                if (1 < value) yield Set.of("PositiveReal");
                yield Set.of("Real");
            }
            case Double value -> {
                if (value == 0) yield Set.of("NonNegativeReal", "Probability");
                if (0 < value && value <= 1) yield Set.of("Probability", "PositiveReal");
                if (1 < value) yield Set.of("PositiveReal");
                yield Set.of("Real");
            }
            case Boolean ignored -> Set.of("Boolean");
            default -> Set.of();
        };
        Set<ResolvedType> resolvedType = typeName.stream()
                .map(x -> ResolvedType.fromString(x, componentResolver))
                .collect(Collectors.toSet());

        return remember(expr, resolvedType);
    }

    @Override
    public Set<ResolvedType> visitStringTemplate(Expr.StringTemplate expr) {
        // we have to make sure that the interpolated expressions are all either strings or numbers

        for (Expr.StringTemplate.Part part : expr.parts) {
            if (part instanceof Expr.StringTemplate.ExpressionPart) {
                Expr interpolatedExpr = ((Expr.StringTemplate.ExpressionPart) part).expression();
                Set<ResolvedType> interpolatedTypeSet = interpolatedExpr.accept(this);

                if (!(
                        TypeUtils.canBeAssignedTo(interpolatedTypeSet, ResolvedType.fromString("String", componentResolver), componentResolver)
                                || TypeUtils.canBeAssignedTo(interpolatedTypeSet, ResolvedType.fromString("Integer", componentResolver), componentResolver)
                                || TypeUtils.canBeAssignedTo(interpolatedTypeSet, ResolvedType.fromString("Real", componentResolver), componentResolver)
                )) {
                    // this variable is neither a string, real, nor integer and cannot be used in a string template
                    throw new TypeError(
                            expr,
                            "Invalid variable in string template.",
                            "You try to use a variable of type '" + printType(interpolatedTypeSet) + "' in a string template. However, only strings and numbers can be used here."
                    );
                }

                // all good
            }
        }

        // the return type is always a string
        return remember(expr, Set.of(ResolvedType.fromString("String", componentResolver)));
    }

    @Override
    public Set<ResolvedType> visitVariable(Expr.Variable expr) {
        String variableName = expr.variableName;
        Set<ResolvedType> resolvedTypeSet = resolveVariable(variableName);

        if (resolvedTypeSet.isEmpty()) {
            // there is no variable with this name
            String closestCandidate = findClosestVariable(variableName);
            throw new TypeError(
                    expr,
                    "Variable `" + variableName + "` does not exist.",
                    closestCandidate.isBlank() ? "" : "Do you mean `" + findClosestVariable(variableName) + "'?"
            );
        }

        return remember(expr, resolvedTypeSet);
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
            throw new TypeError(
                    expr,
                    "Operation `" + TokenType.getLexeme(expr.operator) + "` is not supported for type `" + rightType + "`.",
                    ""
            );
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
                new TypeMatcher.Rule(TokenType.PLUS, "PositiveInteger", "PositiveInteger", "PositiveInteger"),
                new TypeMatcher.Rule(TokenType.PLUS, "PositiveInteger", "PositiveReal", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.PLUS, "PositiveReal", "PositiveInteger", "PositiveReal"),
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
                new TypeMatcher.Rule(TokenType.STAR, "PositiveInteger", "PositiveInteger", "PositiveInteger"),
                new TypeMatcher.Rule(TokenType.STAR, "PositiveInteger", "PositiveReal", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.STAR, "PositiveReal", "PositiveInteger", "PositiveReal"),
                new TypeMatcher.Rule(TokenType.STAR, "NonNegativeReal", "NonNegativeReal", "NonNegativeReal"),
                new TypeMatcher.Rule(TokenType.STAR, "Real", "Real", "NonNegativeReal"),
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
            throw new TypeError(
                    expr,
                    "Operation `" + TokenType.getLexeme(expr.operator) + "` is not supported for types `" + printType(leftType) + "` and `" + printType(rightType) + "`."
            );
        }

        return remember(expr, resultType);
    }

    @Override
    public Set<ResolvedType> visitCall(Expr.Call expr) {
        // resolve arguments

        Map<String, Set<ResolvedType>> resolvedArguments = new HashMap<>();
        String firstArgumentName = null;
        for (int i = 0; i < expr.arguments.length; i++) {
            Expr.Argument argument = expr.arguments[i];

            if (argument.name != null) {
                resolvedArguments.put(argument.name, argument.accept(this));

                if (i == 0) {
                    firstArgumentName = argument.name;
                }
            } else if (argument.expression instanceof Expr.Variable) {
                // any argument can drop the name if a variable name is passed with the same name
                resolvedArguments.put(
                        ((Expr.Variable) argument.expression).variableName,
                        argument.accept(this)
                );

                if (i == 0) {
                    firstArgumentName = ((Expr.Variable) argument.expression).variableName;
                }
            } else if (i == 0) {
                // the first argument does not need a name
                resolvedArguments.put(null, argument.accept(this));
            } else {
                // we are not in a situation where the argument name can be dropped
                throw new TypeError(
                        argument,
                        "Argument name not specified.",
                        "You have to specify the name of the argument here using 'name=<value>'. You can only omit the argument name for the first argument or when your variable has the same name as the argument."
                );
            }
        }

        // fetch all compatible generators
        List<Generator> generators = componentResolver.resolveGenerator(expr.functionName);
        if (generators.isEmpty()) {
            throw new TypeError(
                    expr,
                    "The function `" + expr.functionName + "` does not exist.",
                    "Are you looking for `" + componentResolver.findClosestComponent(expr.functionName) + "'?"
            );
        }

        // check if generators are compatible with arguments
        Set<ResolvedType> possibleReturnTypes = new HashSet<>();
        List<String> errorMessages = new ArrayList<>();
        for (Generator generator : generators) {
            try {
                Set<ResolvedType> possibleGeneratorReturnTypes = TypeUtils.resolveGeneratedType(
                        generator, resolvedArguments, firstArgumentName, componentResolver
                );
                possibleReturnTypes.addAll(possibleGeneratorReturnTypes);
            } catch (TypeError e) {
                errorMessages.add(e.getMessage());
            }
        }

        // throw errors if needed
        if (possibleReturnTypes.isEmpty() && errorMessages.isEmpty()) {
            throw new TypeError(expr, "Function `" + expr.functionName + "` with the given arguments does not exist.");
        } else if (possibleReturnTypes.isEmpty() && errorMessages.size() == 1) {
            throw new TypeError(expr, errorMessages.getFirst());
        } else if (possibleReturnTypes.isEmpty()) {
            String errorMessage = "Function `" + expr.functionName + "` with the given arguments does not exist: \n\t";
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
                        if (x.getName().equals("phylospec.types.Distribution")) {
                            generatedTypeSet.add(x.getParameterTypes().get("T"));
                            return TypeUtils.Visitor.STOP;
                        }
                        return TypeUtils.Visitor.CONTINUE;
                    }, componentResolver
            );
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(
                    expr,
                    "Expression of type `" + printType(resolvedTypeSet) + "` is not a distribution.",
                    "After '~', you always need to provide a distribution. Do you want to use `=` instead?");
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

        // we check the edge case where we have an array of number literals adding up to 1
        boolean onlyNumberLiterals = true;
        double summedUpLiterals = 0.0;
        for (Expr element : expr.elements) {
            if (!(element instanceof Expr.Literal)) {
                onlyNumberLiterals = false;
                break;
            }
            if (!(((Expr.Literal) element).value instanceof Double) && !(((Expr.Literal) element).value instanceof Float)) {
                onlyNumberLiterals = false;
                break;
            }

            summedUpLiterals += (Double) ((Expr.Literal) element).value;
        }
        if (Math.abs(summedUpLiterals - 1.0) < 1e-10) {
            // this is a simplex
            arrayTypeSet.add(ResolvedType.fromString("Simplex", componentResolver));
        }

        return remember(expr, arrayTypeSet);
    }

    @Override
    public Set<ResolvedType> visitListComprehension(Expr.ListComprehension expr) {
        Set<ResolvedType> listTypeSet = expr.list.accept(this);

        // we create a new scope for the list comprehension expression
        // this allows the list comprehension variables to shadow outer variables

        createScope();

        // add the list comprehension variables to the scope

        if (expr.variables.size() == 1) {
            // we only have a single variable

            Set<ResolvedType> variableTypeSet = new HashSet<>();

            for (ResolvedType listType : listTypeSet) {
                // listType is a Vector<T> or a subclass thereof
                // we find the corresponding T
                TypeUtils.visitTypeAndParents(listType, t -> {
                    if (t.getName().equals("Vector")) {
                        variableTypeSet.add(t.getParameterTypes().get("T"));
                        return TypeUtils.Visitor.STOP;
                    }
                    return TypeUtils.Visitor.CONTINUE;
                }, componentResolver);
            }

            remember(expr.variables.getFirst(), variableTypeSet);
        } else if (expr.variables.size() == 2) {
            // we have two variables

            Set<ResolvedType> firstVariableTypeSet = new HashSet<>();
            Set<ResolvedType> secondVariableTypeSet = new HashSet<>();

            for (ResolvedType listType : listTypeSet) {
                // listType is a Vector<Pair<F, S>> or a subclass thereof
                // we find the corresponding F and S
                TypeUtils.visitTypeAndParents(listType, t -> {
                    if (t.getName().equals("Vector")) {
                        ResolvedType pairType = t.getParameterTypes().get("T");

                        if (pairType.getName().equals("Pair")) {
                            firstVariableTypeSet.add(pairType.getParameterTypes().get("F"));
                            secondVariableTypeSet.add(pairType.getParameterTypes().get("S"));
                        }
                    }
                    return TypeUtils.Visitor.CONTINUE;
                }, componentResolver);
            }

            remember(expr.variables.getFirst(), firstVariableTypeSet);
            remember(expr.variables.getLast(), secondVariableTypeSet);
        } else {
            throw new TypeError(expr, "List comprehensions can only have one or two variables.");
        }

        // parse the expression

        Set<ResolvedType> expressionTypeSet = expr.expression.accept(this);
        dropScope();

        // the type of the list comprehension is Vector<type of expression>

        Set<ResolvedType> listComprehensionTypeSet = ResolvedType.fromString(
                "Vector<T>",
                Map.of("T", expressionTypeSet),
                componentResolver
        );
        return remember(expr, listComprehensionTypeSet);
    }

    @Override
    public Set<ResolvedType> visitGet(Expr.Get expr) {
        Set<ResolvedType> objectTypeSet = expr.object.accept(this);

        // we have to look at all possible object types, check if they have the
        // correct method, and collect the corresponding return types

        Set<ResolvedType> returnTypeSet = new HashSet<>();
        boolean foundMatchingProperty = false;

        for (ResolvedType objectType : objectTypeSet) {

        }

        if (!foundMatchingProperty) {
            throw new TypeError(expr, "Property `" + expr.properyName + "` is not known");
        }

        return remember(expr, returnTypeSet);
    }

    @Override
    public Set<ResolvedType> visitIndex(Expr.Index expr) {
        Set<ResolvedType> containerTypeSet = expr.object.accept(this);

        // collect the possible index types and item types based on the resolved container types

        Set<ResolvedType> itemTypeSet = new HashSet<>();
        Set<ResolvedType> indexTypeSet = new HashSet<>();
        Set<Integer> numberOfIndicesRequired = new HashSet<>();

        for (ResolvedType possibleContainerType : containerTypeSet) {
            Map<String, List<ResolvedType>> resolvedTypeParameterTypes = new HashMap<>();
            if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                    "Map<K, V>", possibleContainerType, List.of("K", "V"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("V"));
                indexTypeSet.addAll(resolvedTypeParameterTypes.get("K"));
                numberOfIndicesRequired.add(1);
            }

            resolvedTypeParameterTypes.clear();
            if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                    "Matrix<T>", possibleContainerType, List.of("T"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("T"));
                indexTypeSet.add(ResolvedType.fromString("NonNegativeInteger", componentResolver));
                numberOfIndicesRequired.add(2);
            }

            resolvedTypeParameterTypes.clear();
            if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                    "Vector<T>", possibleContainerType, List.of("T"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("T"));
                indexTypeSet.add(ResolvedType.fromString("NonNegativeInteger", componentResolver));
                numberOfIndicesRequired.add(1);
            }
        }

        // check if we can even index this

        if (numberOfIndicesRequired.isEmpty()) {
            throw new TypeError(
                    "This element cannot be accessed with an index.",
                    "You use the index notation ('items[3]') on an element which is neither a vector, a matrix nor a map. Remove the index."
            );
        }

        // check if there are the right number of indices

        if (!numberOfIndicesRequired.contains(expr.indices.size())) {
            throw new TypeError(
                    "Wrong number of indices provided.",
                    "You provide " + expr.indices.size() + " indices, but " + numberOfIndicesRequired.stream().map(String::valueOf).collect(Collectors.joining("|")) + " are needed."
            );
        }

        // check that the passed indices have the correct type

        for (Expr index : expr.indices) {
            Set<ResolvedType> resolvedIndexTypeSet = index.accept(this);

            // we need at least one match
            boolean hasMatch = false;
            for (ResolvedType possibleIndexType : indexTypeSet) {
                if (TypeUtils.canBeAssignedTo(resolvedIndexTypeSet, possibleIndexType, componentResolver)) {
                    hasMatch = true;
                    break;
                }
            }

            if (!hasMatch) {
                throw new TypeError(
                        "Invalid index.",
                        "Your index is of type '" + printType(resolvedIndexTypeSet) + "'. Only use values of type '" + printType(indexTypeSet) + "' as an index."
                );
            }
        }

        return itemTypeSet;
    }

    @Override
    public Set<ResolvedType> visitRange(Expr.Range range) {
        Set<ResolvedType> fromTypeSet = range.from.accept(this);
        Set<ResolvedType> toTypeSet = range.to.accept(this);

        if (!TypeUtils.canBeAssignedTo(fromTypeSet, ResolvedType.fromString("Integer", componentResolver), componentResolver)) {
            throw new TypeError(
                    range,
                    "The lower bound of the range is not an integer.",
                    "Use an integer expression as lower and upper bounds of a range.",
                    List.of("5:20")
            );
        }
        if (!TypeUtils.canBeAssignedTo(toTypeSet, ResolvedType.fromString("Integer", componentResolver), componentResolver)) {
            throw new TypeError(
                    range,
                    "The upper bound of the range is not an integer.",
                    "Use an integer expression as lower and upper bounds of a range.",
                    List.of("5:20")
            );
        }

        // the type of the items is the cover of the two bounds

        Set<ResolvedType> itemTypeSet = TypeUtils.getLowestCoverTypeSet(List.of(fromTypeSet, toTypeSet), componentResolver);

        // the type of the vector produced is the vector of the item type set

        Set<ResolvedType> listComprehensionTypeSet = ResolvedType.fromString(
                "Vector<T>",
                Map.of("T", itemTypeSet),
                componentResolver
        );

        return remember(range, listComprehensionTypeSet);
    }

    @Override
    public ResolvedType visitAtomicType(AstType.Atomic expr) {
        try {
            return remember(expr, ResolvedType.fromString(expr.name, componentResolver));
        } catch (TypeError e) {
            throw e.attachAstNode(expr);
        }
    }

    @Override
    public ResolvedType visitGenericType(AstType.Generic expr) {
        List<Set<ResolvedType>> typeParameters = new ArrayList<>();

        // resolve the type parameters
        for (AstType type : expr.typeParameters) {
            typeParameters.add(Set.of(type.accept(this)));
        }

        ResolvedType resolvedType = ResolvedType.fromString(expr.name, typeParameters, componentResolver).iterator().next();
        return remember(expr, resolvedType);
    }

    /**
     * helper functions to store the resolved types
     */

    /**
     * Creates a new scope. All remembered variables will be added to this new scope until it is dropped.
     */
    private void createScope() {
        this.scopedVariableTypes.addFirst(new HashMap<>());
    }

    /**
     * Drops the most recent scope. All remembered variables since the creation of that scope are dropped too.
     */
    private void dropScope() {
        this.scopedVariableTypes.removeFirst();
    }

    private ResolvedType remember(Stmt expr, ResolvedType resolvedType) {
        resolvedTypes.put(expr, Set.of(resolvedType));
        return resolvedType;
    }

    private Set<ResolvedType> remember(Expr expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        return resolvedType;
    }

    private ResolvedType remember(AstType expr, ResolvedType resolvedType) {
        resolvedTypes.put(expr, Set.of(resolvedType));
        return resolvedType;
    }

    private Set<ResolvedType> remember(String variableName, Set<ResolvedType> resolvedTypeSet) {
        scopedVariableTypes.getFirst().put(variableName, resolvedTypeSet);
        return resolvedTypeSet;
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
        return String.join(" | ", type.stream().map(TypeResolver::printType).sorted().toList());
    }

    private static String printType(ResolvedType type) {
        String result = type.getShortName();

        if (type.getParameterTypes().isEmpty()) return result;

        result += "<";

        result += String.join(",", type.getParametersNames().stream().map(x -> printType(type.getParameterTypes().get(x))).toList());

        result += ">";
        return result;
    }

    /**
     * helper functions for useful error messages
     */

    private String findClosestVariable(String queryVariable) {
        return getVariableNames().stream()
                .min(Comparator.comparingInt(name -> Utils.editDistance(queryVariable, name)))
                .orElse("");
    }

}