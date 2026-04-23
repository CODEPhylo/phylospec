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
public class TypeResolver implements AstVisitor<Set<ResolvedType>, Set<ResolvedType>, Set<ResolvedType>> {

    private final ComponentResolver componentResolver;
    private final TypeMatcher typeMatcher;

    public Map<AstNode, Set<ResolvedType>> resolvedTypes;
    private final List<Map<String, Set<ResolvedType>>> scopedVariableTypes;

    Unit globalUnit = Unit.IMPLICIT;

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
    public Set<ResolvedType> resolveTypeSet(AstNode expression) {
        return this.resolvedTypes.getOrDefault(expression, Set.of());
    }

    /**
     * Returns the types associated with the given AST type node. Returns null if no
     * type is known.
     */
    public Set<ResolvedType> resolveTypeSet(AstType astTypeNode) {
        return this.resolvedTypes.getOrDefault(astTypeNode, null);
    }

    /**
     * Returns the types associated with the given AST statement node. Returns null if
     * no type is known.
     */
    public Set<ResolvedType> resolveTypeSet(Stmt astTypeNode) {
        return this.resolvedTypes.getOrDefault(astTypeNode, null);
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
    public Set<ResolvedType> visitDecoratedStmt(Stmt.Decorated stmt) {
        if (this.ignoreStmt(stmt)) return Set.of();
        return remember(stmt, stmt.statement.accept(this));
    }

    @Override
    public Set<ResolvedType> visitAssignment(Stmt.Assignment stmt) {
        if (this.ignoreStmt(stmt)) return Set.of();

        Set<ResolvedType> resolvedVariableTypeSet = stmt.type.accept(this);

        Set<ResolvedType> resolvedExpressionTypeSet = stmt.expression.accept(this);

        if (!TypeUtils.canBeAssignedTo(resolvedExpressionTypeSet, resolvedVariableTypeSet, componentResolver)) {
            // TODO: check if distribution and give hint to use ~
            throw new TypeError(
                    stmt,
                    "Expression of type `" + printType(resolvedExpressionTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(resolvedVariableTypeSet) + "`.",
                    "Change the type of the variable to `" + printType(resolvedExpressionTypeSet) + "`, or change what you assign to it."
            );
        }

        // the resolved type variables might be stripped of the type parameters
        // if this is the case, we try to attach the resolved parameter types
        // so far, this only works if the type is not extended

        for (ResolvedType resolvedVariableType : resolvedVariableTypeSet) {
            if (resolvedVariableType.hasUnresolvedParameterTypes()) {
                // find the matching resolved types
                for (ResolvedType resolvedExpressionType : resolvedExpressionTypeSet) {
                    if (resolvedVariableType.getName().equals(resolvedExpressionType.getName())) {
                        resolvedVariableType.getParameterTypes().putAll(
                                resolvedExpressionType.getParameterTypes()
                        );
                    }
                }
            }

            if (resolvedVariableType.hasUnresolvedParameterTypes()) {
                // we weren't able to resolve all parameter types based on the assigned expression
                throw new TypeError(
                        stmt,
                        "Missing type parameters.",
                        "The type '" + resolvedVariableType + "' expects " + resolvedVariableType.getParametersNames().size() + " parameter types, but you specified none. Add the type parameters using brackets.",
                        List.of("Vector<Real>")
                );
            }
        }


        remember(stmt.name, resolvedVariableTypeSet);
        return remember(stmt, resolvedVariableTypeSet);
    }

    @Override
    public Set<ResolvedType> visitDraw(Stmt.Draw stmt) {
        if (this.ignoreStmt(stmt)) return Set.of();

        Set<ResolvedType> resolvedVariableTypeSet = stmt.type.accept(this);

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

        if (!TypeUtils.canBeAssignedTo(generatedTypeSet, resolvedVariableTypeSet, componentResolver)) {
            throw new TypeError(
                    stmt,
                    "Expression of type `" + printType(generatedTypeSet) + "` cannot be assigned to variable `" + stmt.name + "` of type `" + printType(resolvedVariableTypeSet) + "`.",
                    "Change the type of the variable to `" + printType(generatedTypeSet) + "`, or change what value you draw and assign to it."
            );
        }

        // the resolved type variables might be stripped of the type parameters
        // if this is the case, we try to attach the resolved parameter types
        // so far, this only works if the type is not extended

        for (ResolvedType resolvedVariableType : resolvedVariableTypeSet) {
            if (resolvedVariableType.hasUnresolvedParameterTypes()) {
                // find the matching resolved types
                for (ResolvedType generatedType : generatedTypeSet) {
                    if (resolvedVariableType.getName().equals(generatedType.getName())) {
                        resolvedVariableType.getParameterTypes().putAll(
                                generatedType.getParameterTypes()
                        );
                    }
                }
            }

            if (resolvedVariableType.hasUnresolvedParameterTypes()) {
                // we weren't able to resolve all parameter types based on the assigned expression
                throw new TypeError(
                        stmt,
                        "Missing type parameters.",
                        "The type '" + resolvedVariableType + "' expects " + resolvedVariableType.getParametersNames().size() + " parameter types, but you specified none. Add the type parameters using brackets.",
                        List.of("Vector<Real>")
                );
            }
        }

        remember(stmt.name, resolvedVariableTypeSet);
        return remember(stmt, resolvedVariableTypeSet);
    }

    @Override
    public Set<ResolvedType> visitImport(Stmt.Import stmt) {
        try {
            componentResolver.importNamespace(stmt.namespace);
        } catch (TypeError e) {
            throw e.attachAstNode(stmt);
        }
        return null;
    }

    @Override
    public Set<ResolvedType> visitIndexedStmt(Stmt.Indexed indexed) {
        if (this.ignoreStmt(indexed)) return Set.of();

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

        if (indexed.indices.size() != new HashSet<>(indexed.indices).size()) {
            throw new TypeError(
                    indexed,
                    "Duplicate indices.",
                    "You use the same index name multiple times. Use a distinct name for each index."
            );
        }

        // evaluate each range

        List<Set<ResolvedType>> rangeTypeSets = new ArrayList<>();

        for (int i = 0; i < indexed.indices.size(); i++) {
            Expr.Variable indexVar = indexed.indices.get(i);
            Set<ResolvedType> rangeTypeSet = indexed.ranges.get(i).accept(this);
            rangeTypeSets.add(rangeTypeSet);
        }

        // add the corresponding index variable to a new scope

        createScope();

        Set<ResolvedType> innerTypeSet;
        try {
            for (int i = 0; i < indexed.indices.size(); i++) {
                Expr.Variable indexVar = indexed.indices.get(i);
                Set<ResolvedType> rangeTypeSet = rangeTypeSets.get(i);

                if (!TypeUtils.canBeAssignedTo(rangeTypeSet, ResolvedType.fromString("phylospec.types.Vector<NonNegativeInteger>", componentResolver), componentResolver)) {
                    throw new TypeError(
                            indexed,
                            "Index range must produce positive integer values.",
                            "Use integer expressions as the range bounds (e.g., '1:10')."
                    );
                }

                Set<ResolvedType> indexVarTypeSet = new HashSet<>();
                for (ResolvedType rangeType : rangeTypeSet) {
                    ResolvedType indexVarType = TypeUtils.recoverType(
                            "phylospec.types.Vector", rangeType, componentResolver
                    ).getParameterTypes().get("T");
                    if (indexVarType != null) indexVarTypeSet.add(indexVarType);
                }

                remember(indexVar.variableName, indexVarTypeSet);
            }

            // evaluate the inner statement with the index variables in scope

            innerTypeSet = indexed.statement.accept(this);
        } finally {
            dropScope();
        }

        if (innerTypeSet == null) {
            return null;
        }

        // widen the result type: Vector<T> for one index, Matrix<T> for two

        Set<ResolvedType> widenedTypeSet;
        if (indexed.indices.size() == 1) {
            widenedTypeSet = ResolvedType.fromString("phylospec.types.Vector<T>", Map.of("T", innerTypeSet), componentResolver);
        } else {
            widenedTypeSet = ResolvedType.fromString("phylospec.types.Matrix<T>", Map.of("T", innerTypeSet), componentResolver);
        }

        // register the widened type in the outer scope under the variable name

        String variableName = indexed.statement.getName();
        if (variableName != null) {
            remember(variableName, widenedTypeSet);
        }

        return remember(indexed, widenedTypeSet);
    }

    @Override
    public Set<ResolvedType> visitObservedAsStmt(Stmt.ObservedAs observedAs) {
        if (this.ignoreStmt(observedAs)) return Set.of();

        Set<ResolvedType> observationTypeSet = observedAs.observedAs.accept(this);
        Set<ResolvedType> generatedDistributionTypeSet = observedAs.stmt.accept(this);

        // go through generatedDistributionTypeSet, filter the Distribution<> ones and get the actual underlying values

        Set<ResolvedType> generatedTypeSet = new HashSet<>();
        for (ResolvedType generatedDistType : generatedDistributionTypeSet) {
            ResolvedType recoveredDistributionType = TypeUtils.recoverType(
                    "phylospec.types.Distribution", generatedDistType, componentResolver
            );
            if (recoveredDistributionType == null) {
                // this is not a distribution type directly
                // it could still be a random variable though
                generatedTypeSet.add(generatedDistType);
                continue;
            } else {
                ResolvedType generatedType = recoveredDistributionType.getParameterTypes().get("T");
                if (generatedType == null) continue;

                generatedTypeSet.add(generatedType);
            }
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(
                    "Invalid observation.",
                    "You are observing a variable which is not a random variable. You can only observe variables drawn from a distribution."
            );
        }

        // test if the observed value can be assigned to the generated value

        if (!TypeUtils.canBeAssignedTo(
                observationTypeSet, generatedTypeSet, componentResolver
        )) {
            throw new TypeError(
                    observedAs,
                    "Wrong observation type.",
                    "You specify an observation of type '" + printType(observationTypeSet) + "' for a random variable of type '" + printType(generatedTypeSet) + "'. Use an observation of type '" + printType(generatedTypeSet) + "' instead."
            );
        }

        return generatedDistributionTypeSet;
    }

    @Override
    public Set<ResolvedType> visitObservedBetweenStmt(Stmt.ObservedBetween observedBetween) {
        if (this.ignoreStmt(observedBetween)) return Set.of();

        Set<ResolvedType> observationFromTypeSet = observedBetween.observedFrom.accept(this);
        Set<ResolvedType> observationToTypeSet = observedBetween.observedTo.accept(this);

        Set<ResolvedType> scalarTypeSet = ResolvedType.fromString("phylospec.types.Integer", componentResolver);
        scalarTypeSet.addAll(ResolvedType.fromString("phylospec.types.Real", componentResolver));

        if (!TypeUtils.canBeAssignedTo(
                observationFromTypeSet, scalarTypeSet, componentResolver
        )) {
            throw new TypeError(
                    "Invalid observation.",
                    "Observations ranges have to be specified using numbers."
            );
        }

        if (!TypeUtils.canBeAssignedTo(
                observationToTypeSet, scalarTypeSet, componentResolver
        )) {
            throw new TypeError(
                    "Invalid observation.",
                    "Observations ranges have to be specified using numbers."
            );
        }

        Set<ResolvedType> observationTypeSet = TypeUtils.getLowestCoverTypeSet(
                List.of(observationFromTypeSet, observationToTypeSet), componentResolver
        );

        Set<ResolvedType> generatedDistributionTypeSet = observedBetween.stmt.accept(this);

        // go through generatedDistributionTypeSet, filter the Distribution<> ones and get the actual underlying values

        Set<ResolvedType> generatedTypeSet = new HashSet<>();
        for (ResolvedType generatedDistType : generatedDistributionTypeSet) {
            ResolvedType recoveredDistributionType = TypeUtils.recoverType(
                    "phylospec.types.Distribution", generatedDistType, componentResolver
            );
            if (recoveredDistributionType == null) {
                // this is not a distribution type directly
                // it could still be a random variable though
                generatedTypeSet.add(generatedDistType);
                continue;
            } else {
                ResolvedType generatedType = recoveredDistributionType.getParameterTypes().get("T");
                if (generatedType == null) continue;

                generatedTypeSet.add(generatedType);
            }
        }

        if (generatedTypeSet.isEmpty()) {
            throw new TypeError(
                    "Invalid observation.",
                    "You are observing a variable which is not a random variable. You can only observe variables drawn from a distribution."
            );
        }

        // test if the observed value can be assigned to the generated value

        if (!TypeUtils.canBeAssignedTo(
                observationTypeSet, generatedTypeSet, componentResolver
        )) {
            throw new TypeError(
                    observedBetween,
                    "Wrong observation type.",
                    "You specify an observation of type '" + printType(observationTypeSet) + "' for a random variable of type '" + printType(generatedTypeSet) + "'. Use an observation of type '" + printType(generatedTypeSet) + "' instead."
            );
        }

        return generatedDistributionTypeSet;
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

        Set<ResolvedType> resolvedTypeSet = new HashSet<>();
        for (String name : typeName) {
            resolvedTypeSet.addAll(ResolvedType.fromString(name, componentResolver));
        }

        if (expr.unit != Unit.IMPLICIT) {
            // this literal has an explicit unit

            // check that it matches a previously used explicit unit

            if (globalUnit != Unit.IMPLICIT && expr.unit != globalUnit) {
                throw new TypeError(
                        "Multiple units are not allowed.",
                        "You use the unit '" + expr.unit + "', but have previously used the unit '" + globalUnit + "'. You can only use a single unit in a model. Convert this value to '" + globalUnit + "'.",
                        List.of("100 " + globalUnit)
                );
            }

            // check that we have a number literal

            Set<ResolvedType> scalarTypeSet = ResolvedType.fromString("phylospec.types.Real", componentResolver);
            scalarTypeSet.addAll(ResolvedType.fromString("phylospec.types.Integer", componentResolver));
            if (!TypeUtils.canBeAssignedTo(resolvedTypeSet, scalarTypeSet, componentResolver)) {
                throw new TypeError(
                        "Only numbers can have units.",
                        "You specify a unit for a value of the type '" + printType(resolvedTypeSet) + "'. Only numbers can have units."
                );
            }

            // set the global unit

            globalUnit = expr.unit;
        }

        return remember(expr, resolvedTypeSet);
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
        return remember(expr, ResolvedType.fromString("String", componentResolver));
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
    public Set<ResolvedType> visitTemplateVariable(Expr.TemplateVariable expr) {
        // template variables are not allowed in normal PhyloSpec models
        throw new TypeError(
                "Template variables are not allowed.",
                "You use a variable name starting with a '$'. This is not allowed in a PhyloSpec model. Use a variable name without a dollar symbol."
        );
    }

    @Override
    public Set<ResolvedType> visitOptionalTemplateVariable(Expr.OptionalTemplateVariable expr) {
        // optional template variables are not allowed in normal PhyloSpec models
        throw new TypeError(
                "Template variables are not allowed.",
                "You use a variable name starting with a '$$'. This is not allowed in a PhyloSpec model. Use a variable name without a dollar symbol."
        );
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
                    "Operation `" + TokenType.getLexeme(expr.operator) + "` is not supported for type `" + rightType + "`."
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

        /* Rules for argument names:
         * (1) If an argument name is given, the value is assigned that argument.
         * If no argument name is given:
         *      (2) If only one argument is passed, the value is assigned to the single required argument.
         *      (3) If a variable is passed, the value is assigned the argument with the name of the variable.
         *      (4) If the value is not a variable, and this is the first given argument, it is assigned the first argument.
         * (5) If any argument has two values assigned to it, it is an error.
         */

        Map<String, Set<ResolvedType>> resolvedArguments = new HashMap<>();
        String firstArgumentName = null;
        for (int i = 0; i < expr.arguments.length; i++) {
            Expr.Argument argument = expr.arguments[i];

            if (argument.name != null) {
                // rule (1)
                resolvedArguments.put(argument.name, argument.accept(this));
            } else if (argument.expression instanceof Expr.Variable variable) {
                // any argument can drop the name if a variable name is passed with the same name
                if (resolvedArguments.containsKey(variable.variableName)) {
                    // rule (5)
                    throw new TypeError(
                            argument,
                            "Argument '" + variable.variableName + "' specified multiple times.",
                            "You have already specified the argument with the name of this variable. If you want to use the variable for a different argument than '" + variable.variableName + "', set it explicitly with '<argument>=" + variable.variableName + "'."
                    );
                }

                // rule (3)
                resolvedArguments.put(
                        variable.variableName,
                        argument.accept(this)
                );

                if (expr.arguments.length == 1) {
                    // if there is only one argument given, the given value is always assigned
                    // rule (2)
                    firstArgumentName = variable.variableName;
                }
            } else if (i == 0) {
                // the first argument does not need a name
                // rule (4)
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
        List<TypeError> errors = new ArrayList<>();
        for (Generator generator : generators) {
            try {
                Set<ResolvedType> possibleGeneratorReturnTypes = TypeUtils.resolveGeneratedType(
                        generator, resolvedArguments, firstArgumentName, componentResolver
                );
                possibleReturnTypes.addAll(possibleGeneratorReturnTypes);
            } catch (TypeError e) {
                e.attachAstNode(expr);
                errors.add(e);
            }
        }

        // throw errors if needed
        if (possibleReturnTypes.isEmpty() && errors.isEmpty()) {
            throw new TypeError(expr, "Function `" + expr.functionName + "` with the given arguments does not exist.");
        } else if (possibleReturnTypes.isEmpty() && errors.size() == 1) {
            throw errors.getFirst();
        } else if (possibleReturnTypes.isEmpty()) {
            String description = "Function `" + expr.functionName + "` with the given arguments does not exist.";

            StringBuilder hint = new StringBuilder("There are " + generators.size() + " different versions of '" + expr.functionName + "'. They cannot be used due to the following reasons:\n");

            for (int i = 0; i < errors.size(); i++) {
                hint.append("\n ").append(i + 1).append(". ").append(errors.get(i).getMessage());
            }

            hint.append("\n\nFix any of these issues to use '").append(expr.functionName).append("'.");

            throw new TypeError(expr, description, hint.toString());
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

        Type vectorComponent = componentResolver.resolveType("phylospec.types.Vector");
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

            summedUpLiterals += ((Number) ((Expr.Literal) element).value).doubleValue();
        }
        if (Math.abs(summedUpLiterals - 1.0) < 1e-10) {
            // this is a simplex
            arrayTypeSet.addAll(ResolvedType.fromString("phylospec.types.Simplex", componentResolver));
        }

        return remember(expr, arrayTypeSet);
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
                    "phylospec.types.Map<K, V>", possibleContainerType, List.of("K", "V"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("V"));
                indexTypeSet.addAll(resolvedTypeParameterTypes.get("K"));
                numberOfIndicesRequired.add(1);
            }

            resolvedTypeParameterTypes.clear();
            if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                    "phylospec.types.Matrix<T>", possibleContainerType, List.of("T"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("T"));
                indexTypeSet.addAll(ResolvedType.fromString("phylospec.types.NonNegativeInteger", componentResolver));
                numberOfIndicesRequired.add(2);
            }

            resolvedTypeParameterTypes.clear();
            if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                    "phylospec.types.Vector<T>", possibleContainerType, List.of("T"), resolvedTypeParameterTypes, componentResolver
            )) {
                itemTypeSet.addAll(resolvedTypeParameterTypes.get("T"));
                indexTypeSet.addAll(ResolvedType.fromString("phylospec.types.NonNegativeInteger", componentResolver));
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
            if (!TypeUtils.canBeAssignedTo(resolvedIndexTypeSet, indexTypeSet, componentResolver)) {
                throw new TypeError(
                        "Invalid index.",
                        "Your index is of type '" + printType(resolvedIndexTypeSet) + "'. Only use values of type '" + printType(indexTypeSet) + "' as an index."
                );
            }
        }

        return remember(expr, itemTypeSet);
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
                "phylospec.types.Vector<T>",
                Map.of("T", itemTypeSet),
                componentResolver
        );

        return remember(range, listComprehensionTypeSet);
    }

    @Override
    public Set<ResolvedType> visitAtomicType(AstType.Atomic expr) {
        try {
            return remember(expr, ResolvedType.fromString(expr.name, componentResolver, true));
        } catch (TypeError e) {
            throw e.attachAstNode(expr);
        }
    }

    @Override
    public Set<ResolvedType> visitGenericType(AstType.Generic expr) {
        List<Set<ResolvedType>> typeParameters = new ArrayList<>();

        // resolve the type parameters
        for (AstType type : expr.typeParameters) {
            typeParameters.add(type.accept(this));
        }

        Set<ResolvedType> resolvedType = ResolvedType.fromString(expr.name, typeParameters, componentResolver, true);
        return remember(expr, resolvedType);
    }

    /**
     * Check if the type resolver ignores the statement because it is in a custom block.
     */
    private boolean ignoreStmt(Stmt stmt) {
        Set<Stmt.Block> blocksConsidered = Set.of(Stmt.Block.NO_BLOCK, Stmt.Block.MODEL, Stmt.Block.DATA);
        return (!blocksConsidered.contains(stmt.block));
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

    private Set<ResolvedType> remember(Stmt expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        return resolvedType;
    }

    private Set<ResolvedType> remember(Expr expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
        return resolvedType;
    }

    private Set<ResolvedType> remember(AstType expr, Set<ResolvedType> resolvedType) {
        resolvedTypes.put(expr, resolvedType);
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
        String result = type.getUnqualifiedName();

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