package org.phylospec.typeresolver.properties;

import java.util.*;
import org.phylospec.components.Generator;
import org.phylospec.components.ParsedType;
import org.phylospec.components.ParsedTypeProperty;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeUtils;

public class TypePropertyResolver {

    private static List<TypePropertyResolverHook> hooks = new ArrayList<>();

    static {
        // load hooks
        for (TypePropertyResolverHook hook : ServiceLoader.load(TypePropertyResolverHook.class)) {
            hooks.add(hook);
        }
    }

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
                    generator.getName(),
                    generator.getNamespace(),
                    parsedGeneratedType,
                    generatedType,
                    resolvedGeneratorApplication.resolvedArguments());
        }
    }

    private static void resolveTypeProperties(
            String generatorName,
            String namespace,
            ParsedType parsedType,
            ResolvedType generatedType,
            Map<String, Set<ResolvedType>> resolvedArguments) {
        // resolve potential hooks

        for (TypePropertyResolverHook hook : hooks) {
            if (hook.getGenerator().equals(namespace + "." + generatorName)) {
                hook.attemptResolution(parsedType, generatedType, resolvedArguments);
            }
        }

        // resolve the type properties of generatedType

        for (ParsedTypeProperty typeProperty : parsedType.getTypeProperties()) {
            if (generatedType.hasPropertyAttached(typeProperty.getPropertyName())) {
                // we have already resolved this property name
                // we don't do it again
                continue;
            }

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
                        generatorName,
                        namespace,
                        parsedTypeParameters.get(index),
                        resolvedTypeParameter,
                        resolvedArguments);
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
