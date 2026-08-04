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
import org.phylospec.typeresolver.TypeUtils;

/**
 * Resolves and validates type properties for generator applications. This includes declared
 * property constraints, generator-specific property providers, and properties declared on the
 * generated type.
 */
public class GeneratorPropertyResolver {

    private final List<ErrorEventListener> eventListeners;
    private final List<GeneratorPropertyProvider> providers;

    public GeneratorPropertyResolver() {
        eventListeners = new ArrayList<>();
        providers = GeneratorPropertyProvider.loadProviders();
    }

    public void registerEventListener(ErrorEventListener listener) {
        this.eventListeners.add(listener);
    }

    private void raiseWarning(Error warning) {
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.warningDetected(warning);
        }
    }

    public void processGenerator(
            Expr.Call call,
            Generator generator,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        ParsedType parsedGeneratedType = new ParsedType(generator.getGeneratedType());
        checkConstraints(call, generator, resolvedGeneratorApplication);
        resolveProviders(generator, parsedGeneratedType, resolvedGeneratorApplication);
        resolveGenerator(parsedGeneratedType, resolvedGeneratorApplication);
    }

    private void checkConstraints(
            Expr.Call call,
            Generator generator,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        Map<String, Set<ResolvedType>> resolvedArguments =
                resolvedGeneratorApplication.resolvedArguments();

        for (String constraintString : generator.getConstraints()) {
            checkGenerator(call, constraintString, resolvedArguments);
        }
    }

    private void checkGenerator(
            Expr.Call call,
            String constraintString,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        ParsedTypeConstraint constraint = new ParsedTypeConstraint(constraintString);

        Set<ResolvedType> leftInputTypeSet = resolvedArguments.get(constraint.getLeftInputName());
        Set<ResolvedType> rightInputTypeSet = resolvedArguments.get(constraint.getRightInputName());

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
                Object leftProperty =
                        leftInputType.properties().get(constraint.getLeftPropertyName());
                Object rightProperty =
                        rightInputType.properties().get(constraint.getRightPropertyName());

                if (!(leftProperty instanceof Number leftNr)
                        || !(rightProperty instanceof Number rightNr)) {
                    // these are somehow not numbers, or we don't know these properties
                    return;
                }

                boolean fulfilled =
                        switch (constraint.getConstraintType()) {
                            case EQUALITY -> leftNr.doubleValue() == rightNr.doubleValue();
                            case INEQUALITY -> leftNr.doubleValue() != rightNr.doubleValue();
                            case LESS -> leftNr.doubleValue() < rightNr.doubleValue();
                            case LESS_THAN -> leftNr.doubleValue() <= rightNr.doubleValue();
                            case GREATER -> leftNr.doubleValue() > rightNr.doubleValue();
                            case GREATER_THAN -> leftNr.doubleValue() >= rightNr.doubleValue();
                        };

                if (fulfilled) {
                    return;
                }
            }
        }

        // all type combinations could be evaluated and none of them were successful

        raiseWarning(
                new Error(
                        call.getRange(),
                        "The inputs for '" + call.functionName + "' might be invalid.",
                        constraint.errorMessage()));
    }

    private void resolveProviders(
            Generator generator,
            ParsedType parsedGeneratedType,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        Map<String, Set<ResolvedType>> resolvedArguments =
                resolvedGeneratorApplication.resolvedArguments();

        for (ResolvedType generatedType : resolvedGeneratorApplication.generatedTypeSet()) {
            for (GeneratorPropertyProvider provider : providers) {
                if (provider.getGenerator()
                        .equals(generator.getNamespace() + "." + generator.getName())) {
                    provider.resolveGenerator(generatedType, resolvedArguments);
                }
            }
        }
    }

    private void resolveGenerator(
            ParsedType parsedGeneratedType,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        for (ResolvedType generatedType : resolvedGeneratorApplication.generatedTypeSet()) {
            resolveTypeProperties(
                    parsedGeneratedType,
                    generatedType,
                    resolvedGeneratorApplication.resolvedArguments());
        }
    }

    private void resolveTypeProperties(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        // resolve the type properties of generatedType

        for (ParsedTypeProperty typeProperty : parsedType.getTypeProperties()) {
            if (generatedType.properties().has(typeProperty.getPropertyName())) {
                // we have already resolved this property name
                // we don't do it again
                continue;
            }

            String propertyName = typeProperty.getPropertyName();

            if (typeProperty instanceof ParsedTypeProperty.Constant constant) {
                generatedType
                        .properties()
                        .attach(propertyName, resolveConstant(constant.getValue()));
            } else if (typeProperty instanceof ParsedTypeProperty.Assignment assignment) {
                // this is an assignment (propertyName = inputName.inputPropertyName)
                Set<ResolvedType> resolvedTypeSet =
                        resolvedArguments.get(assignment.getInputName());

                if (resolvedTypeSet == null || resolvedTypeSet.isEmpty()) {
                    // we cannot determine the input types
                    // we don't resolve this property
                    continue;
                }

                // we now check all possible input types and resolve the property value if they all
                // agree
                // this is not the most general way to handle this, as there might be disagreements
                // and then we lose track of that information

                Set<Object> possiblePropertyValues = new HashSet<>();
                for (ResolvedType resolvedType : resolvedTypeSet) {
                    Object propertyValue =
                            resolvedType.properties().get(assignment.getInputPropertyName());

                    if (propertyValue == null) {
                        // this input type does not have this property
                        // we clear possiblePropertyValues such that existing property values are
                        // not
                        // resolved
                        possiblePropertyValues.clear();
                        break;
                    }

                    possiblePropertyValues.add(propertyValue);
                }

                if (possiblePropertyValues.size() == 1) {
                    // all candidate property values agree
                    // we resolve it
                    generatedType
                            .properties()
                            .attach(propertyName, possiblePropertyValues.iterator().next());
                }
            }
        }

        // recursively resolve properties of the generic type parameters

        List<ParsedType> parsedTypeParameters = parsedType.getTypeParameters();
        List<String> resolvedTypeParameterNames = generatedType.getParametersNames();
        int parameterCount =
                Math.min(parsedTypeParameters.size(), resolvedTypeParameterNames.size());

        for (int index = 0; index < parameterCount; index++) {
            ResolvedType resolvedTypeParameter =
                    generatedType.getParameterTypes().get(resolvedTypeParameterNames.get(index));
            if (resolvedTypeParameter != null) {
                resolveTypeProperties(
                        parsedTypeParameters.get(index), resolvedTypeParameter, resolvedArguments);
            }
        }
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
