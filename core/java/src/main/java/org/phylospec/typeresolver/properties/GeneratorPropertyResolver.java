package org.phylospec.typeresolver.properties;

import java.util.*;
import org.phylospec.ast.Expr;
import org.phylospec.components.Generator;
import org.phylospec.components.ParsedType;
import org.phylospec.components.ParsedTypeConstraint;
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

        for (String constraintString : generator.getConstraints()) {
            checkConstraint(call, constraintString, resolvedArguments);
        }
    }

    private void checkConstraint(
            Expr.Call call, String constraintString, Map<String, ResolvedTypeSet> resolvedArguments) {
        ParsedTypeConstraint constraint = ParsedTypeConstraint.parse(constraintString);

        if (constraint instanceof ParsedTypeConstraint.PropertyComparison propertyComparison) {
            checkPropertyConstraint(call, propertyComparison, resolvedArguments);
        } else if (constraint instanceof ParsedTypeConstraint.ConstantComparison constantComparison) {
            checkConstantConstraint(call, constantComparison, resolvedArguments);
        }
    }

    private void checkPropertyConstraint(
            Expr.Call call,
            ParsedTypeConstraint.PropertyComparison constraint,
            Map<String, ResolvedTypeSet> resolvedArguments) {
        ResolvedTypeSet leftInputTypeSet = resolvedArguments.get(constraint.getLeftInputName());
        ResolvedTypeSet rightInputTypeSet = resolvedArguments.get(constraint.getRightInputName());

        if (leftInputTypeSet == null || rightInputTypeSet == null) {
            // we don't know about this input
            // let's ignore this constraint
            return;
        }

        if (leftInputTypeSet.isEmpty() || rightInputTypeSet.isEmpty()) {
            // there are no possible types. this is an issue but not related to this constraint
            return;
        }

        for (ResolvedType leftInputType : leftInputTypeSet) {
            for (ResolvedType rightInputType : rightInputTypeSet) {
                Object leftProperty = leftInputType.properties().get(constraint.getLeftPropertyName());
                Object rightProperty = rightInputType.properties().get(constraint.getRightPropertyName());

                if (!(leftProperty instanceof Number leftNr) || !(rightProperty instanceof Number rightNr)) {
                    // these are somehow not numbers, or we don't know these properties
                    return;
                }

                if (isFulfilled(constraint, leftNr.doubleValue(), rightNr.doubleValue())) {
                    // there are type inputs for which the constraint could be fulfilled :)
                    return;
                }
            }
        }

        // all type combinations could be evaluated and none of them were successful

        raiseConstraintWarning(call, constraint);
    }

    private void checkConstantConstraint(
            Expr.Call call,
            ParsedTypeConstraint.ConstantComparison constraint,
            Map<String, ResolvedTypeSet> resolvedArguments) {
        ResolvedTypeSet leftInputTypeSet = resolvedArguments.get(constraint.getLeftInputName());

        if (leftInputTypeSet == null) {
            // we don't know about this input
            // let's ignore this constraint
            return;
        }

        if (leftInputTypeSet.isEmpty()) {
            // there are no possible types. this is an issue but not related to this constraint
            return;
        }

        for (ResolvedType leftInputType : leftInputTypeSet) {
            Object leftProperty = leftInputType.properties().get(constraint.getLeftPropertyName());

            if (!(leftProperty instanceof Number leftNr)) {
                // this is somehow not a number, or we don't know this property
                return;
            }

            if (isFulfilled(constraint, leftNr.doubleValue(), constraint.getConstant())) {
                // there are type inputs for which the constraint could be fulfilled :)
                return;
            }
        }

        // all types could be evaluated and none of them were successful

        raiseConstraintWarning(call, constraint);
    }

    private static boolean isFulfilled(ParsedTypeConstraint constraint, double leftValue, double rightValue) {
        return switch (constraint.getConstraintType()) {
            case EQUALITY -> leftValue == rightValue;
            case INEQUALITY -> leftValue != rightValue;
            case LESS -> leftValue < rightValue;
            case LESS_THAN -> leftValue <= rightValue;
            case GREATER -> leftValue > rightValue;
            case GREATER_THAN -> leftValue >= rightValue;
        };
    }

    private void raiseConstraintWarning(Expr.Call call, ParsedTypeConstraint constraint) {
        raiseWarning(new Error(
                call.getRange(),
                "The inputs for '" + call.functionName + "' might be invalid.",
                constraint.errorMessage()));
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
