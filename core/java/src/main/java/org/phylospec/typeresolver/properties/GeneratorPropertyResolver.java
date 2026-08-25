package org.phylospec.typeresolver.properties;

import java.util.*;
import org.phylospec.ast.Expr;
import org.phylospec.components.Constraint;
import org.phylospec.components.Generator;
import org.phylospec.components.ParsedType;
import org.phylospec.components.ParsedTypeProperty;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.ResolvedTypeSet;
import org.phylospec.typeresolver.TypeUtils;
import org.phylospec.workspace.Workspace;

/**
 * Resolves and validates type properties for generator applications. This includes declared
 * property constraints, generator-specific property providers, and properties declared on the
 * generated type.
 */
public class GeneratorPropertyResolver {

    private final List<ErrorEventListener> eventListeners;
    private final List<GeneratorPropertyProvider> providers;
    private final Workspace workspace;

    public GeneratorPropertyResolver(Workspace workspace) {
        this.eventListeners = new ArrayList<>();
        this.providers = GeneratorPropertyProvider.loadProviders();
        this.workspace = workspace;
    }

    public void registerEventListener(ErrorEventListener listener) {
        this.eventListeners.add(listener);
    }

    private void raiseWarning(Error warning) {
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.warningDetected(warning);
        }
    }

    /**
     * Checks the generator's declared constraints, runs any generator-specific property
     * providers, and resolves the properties declared on the generated type, for a single call to
     * the given generator.
     */
    public void processGenerator(
            Expr.Call call, Generator generator, TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        ParsedType parsedGeneratedType = new ParsedType(generator.getGeneratedType());
        checkConstraints(call, generator, resolvedGeneratorApplication);
        resolveProviders(generator, resolvedGeneratorApplication);
        resolveGenerator(parsedGeneratedType, resolvedGeneratorApplication);
    }

    private void checkConstraints(
            Expr.Call call, Generator generator, TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        Map<String, ResolvedTypeSet> resolvedArguments = resolvedGeneratorApplication.resolvedArguments();

        for (Constraint constraint : generator.getConstraints()) {
            checkConstraint(call, constraint, resolvedArguments);
        }
    }

    private void checkConstraint(
            Expr.Call call, Constraint constraint, Map<String, ResolvedTypeSet> resolvedArguments) {
        List<Double> leftValues = possibleValues(resolvedArguments, constraint.getArgument(), constraint.getProperty());
        List<Double> rightValues = constraint.getConstant() != null
                ? List.of(constraint.getConstant())
                : possibleValues(resolvedArguments, constraint.getOtherArgument(), constraint.getOtherProperty());

        if (leftValues == null || rightValues == null) {
            // we cannot evaluate this constraint, so we ignore it
            return;
        }

        for (double leftValue : leftValues) {
            for (double rightValue : rightValues) {
                if (isFulfilled(constraint.getOperator(), leftValue, rightValue)) {
                    // there are type inputs for which the constraint could be fulfilled :)
                    return;
                }
            }
        }

        // all type combinations could be evaluated and none of them were successful

        raiseConstraintWarning(call, constraint);
    }

    /**
     * Collects the possible values of a numeric type property of the given input.
     *
     * @param resolvedArguments the resolved types of all inputs
     * @param inputName the name of the input carrying the property
     * @param propertyName the property name
     * @return the possible values, or null if they cannot be determined
     */
    private static List<Double> possibleValues(
            Map<String, ResolvedTypeSet> resolvedArguments, String inputName, String propertyName) {
        ResolvedTypeSet inputTypeSet = resolvedArguments.get(inputName);

        if (inputTypeSet == null || inputTypeSet.isEmpty()) {
            // we don't know about this input, or there are no possible types
            return null;
        }

        List<Double> values = new ArrayList<>();

        for (ResolvedType inputType : inputTypeSet) {
            Object property = inputType.properties().get(propertyName);

            if (!(property instanceof Number number)) {
                // this is somehow not a number, or we don't know this property
                return null;
            }

            values.add(number.doubleValue());
        }

        return values;
    }

    private static boolean isFulfilled(Constraint.Operator operator, double leftValue, double rightValue) {
        return switch (operator) {
            case EQUALS -> leftValue == rightValue;
            case NOT_EQUALS -> leftValue != rightValue;
            case LESS_THAN -> leftValue < rightValue;
            case LESS_THAN_OR_EQUAL -> leftValue <= rightValue;
            case GREATER_THAN -> leftValue > rightValue;
            case GREATER_THAN_OR_EQUAL -> leftValue >= rightValue;
        };
    }

    private void raiseConstraintWarning(Expr.Call call, Constraint constraint) {
        raiseWarning(new Error(
                call.getRange(),
                "The inputs for '" + call.functionName + "' might be invalid.",
                describeConstraint(constraint)));
    }

    /**
     * Describes a violated constraint in a human-readable way.
     *
     * @param constraint the violated constraint
     * @return the message
     */
    private static String describeConstraint(Constraint constraint) {
        return TypePropertyNames.describeProperty(constraint.getArgument(), constraint.getProperty(), true)
                + " must "
                + describeOperator(constraint.getOperator())
                + " "
                + describeRightSide(constraint)
                + ".";
    }

    private static String describeRightSide(Constraint constraint) {
        if (constraint.getConstant() != null) {
            return describeConstant(constraint.getConstant());
        }

        return TypePropertyNames.describeProperty(constraint.getOtherArgument(), constraint.getOtherProperty(), false);
    }

    private static String describeConstant(double constant) {
        // print whole numbers without a trailing `.0`

        if (constant == Math.rint(constant) && !Double.isInfinite(constant)) {
            return Long.toString((long) constant);
        }

        return Double.toString(constant);
    }

    private static String describeOperator(Constraint.Operator operator) {
        return switch (operator) {
            case EQUALS -> "be equal to";
            case NOT_EQUALS -> "be different from";
            case LESS_THAN -> "be less than";
            case LESS_THAN_OR_EQUAL -> "be less than or equal to";
            case GREATER_THAN -> "be greater than";
            case GREATER_THAN_OR_EQUAL -> "be greater than or equal to";
        };
    }

    private void resolveProviders(
            Generator generator, TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        Map<String, ResolvedTypeSet> resolvedArguments = resolvedGeneratorApplication.resolvedArguments();

        for (GeneratorPropertyProvider provider : providers) {
            if (provider.getGenerator().equals(generator.getNamespace() + "." + generator.getName())) {
                for (ResolvedType generatedType : resolvedGeneratorApplication.generatedTypeSet()) {
                    provider.resolveGenerator(generatedType, resolvedArguments, workspace);
                }
            }
        }
    }

    private void resolveGenerator(
            ParsedType parsedGeneratedType, TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        for (ResolvedType generatedType : resolvedGeneratorApplication.generatedTypeSet()) {
            resolveTypeProperties(parsedGeneratedType, generatedType, resolvedGeneratorApplication.resolvedArguments());
        }
    }

    private void resolveTypeProperties(
            ParsedType parsedType, ResolvedType generatedType, Map<String, ResolvedTypeSet> resolvedArguments) {
        // resolve the type properties of generatedType
        // this could be either a constant assignment (e.g. `Vector<Real; num=10`) or an assignment
        // of an input type property (e.g. `numBranches=inputTree.numBranches`).

        for (ParsedTypeProperty typeProperty : parsedType.getTypeProperties()) {
            if (generatedType.properties().has(typeProperty.getPropertyName())) {
                // we have already resolved this property name
                // we don't do it again
                continue;
            }

            String propertyName = typeProperty.getPropertyName();

            if (typeProperty instanceof ParsedTypeProperty.Constant constant) {
                // this is a constant assignment (propertyName = constant)

                generatedType.properties().attach(propertyName, resolveConstant(constant.getValue()));

            } else if (typeProperty instanceof ParsedTypeProperty.Assignment assignment) {
                // this is an input type assignment (propertyName = inputName.inputPropertyName)

                generatedType.properties().attach(propertyName, resolveAssignment(resolvedArguments, assignment));
            }
        }

        // recursively resolve properties of the generic type parameters

        List<ParsedType> parsedTypeParameters = parsedType.getTypeParameters();
        List<String> resolvedTypeParameterNames = generatedType.getParametersNames();
        int parameterCount = Math.min(parsedTypeParameters.size(), resolvedTypeParameterNames.size());

        for (int index = 0; index < parameterCount; index++) {
            ResolvedType resolvedTypeParameter =
                    generatedType.getParameterTypes().get(resolvedTypeParameterNames.get(index));
            if (resolvedTypeParameter != null) {
                resolveTypeProperties(parsedTypeParameters.get(index), resolvedTypeParameter, resolvedArguments);
            }
        }
    }

    private static Object resolveAssignment(
            Map<String, ResolvedTypeSet> resolvedArguments, ParsedTypeProperty.Assignment assignment) {
        ResolvedTypeSet resolvedTypeSet = resolvedArguments.get(assignment.getInputName());

        if (resolvedTypeSet == null || resolvedTypeSet.isEmpty()) {
            // we cannot determine the input types
            // we don't resolve this property
            return null;
        }

        // we now check all possible input types and resolve the property value if they all
        // agree
        // this is not the most general way to handle this, as there might be disagreements
        // and then we lose track of that information

        Set<Object> possiblePropertyValues = new HashSet<>();
        for (ResolvedType resolvedType : resolvedTypeSet) {
            Object propertyValue = resolvedType.properties().get(assignment.getInputPropertyName());

            if (propertyValue == null) {
                // this input type does not have this property
                return null;
            }

            possiblePropertyValues.add(propertyValue);
        }

        if (possiblePropertyValues.size() != 1) {
            // not all property values agree
            // we don't resolve this property
            return null;
        }

        // all property values agree
        return possiblePropertyValues.iterator().next();
    }

    private static Object resolveConstant(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.valueOf(value);
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            // this is not an integer
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            // this is not a long
        }

        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            // this is not a floating-point number
        }

        return value;
    }
}
