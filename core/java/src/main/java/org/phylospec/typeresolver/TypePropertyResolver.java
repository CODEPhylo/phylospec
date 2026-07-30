package org.phylospec.typeresolver;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.phylospec.components.Generator;
import org.phylospec.components.ParsedType;
import org.phylospec.components.ParsedTypeProperty;

public class TypePropertyResolver {

    /**
     * Resolves properties declared by a generator's generated type and attaches them to every
     * possible resolved generated type.
     *
     * @param generator the generator declaring the generated type and its properties
     * @param resolvedGeneratorApplication the resolved generated types and input argument types
     */
    public static void resolveTypeProperties(
            Generator generator,
            TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        ParsedType parsedGeneratedType = new ParsedType(generator.getGeneratedType());
        for (ResolvedType generatedType : resolvedGeneratorApplication.generatedTypeSet()) {
            resolveTypeProperties(
                    parsedGeneratedType,
                    generatedType,
                    resolvedGeneratorApplication.resolvedArguments());
        }
    }

    private static void resolveTypeProperties(
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        // resolve the type properties of generatedType

        for (ParsedTypeProperty typeProperty : parsedType.getTypeProperties()) {
            String propertyName = typeProperty.getPropertyName();

            if (typeProperty instanceof ParsedTypeProperty.Constant constant) {
                generatedType.attachProperty(propertyName, parseConstant(constant.getValue()));
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
                            resolvedType.getProperty(assignment.getInputPropertyName());

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
                    generatedType.attachProperty(
                            propertyName, possiblePropertyValues.iterator().next());
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

    private static Object parseConstant(String value) {
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
