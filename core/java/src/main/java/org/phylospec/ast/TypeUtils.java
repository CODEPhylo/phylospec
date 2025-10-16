package org.phylospec.ast;

import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;

import java.util.*;
import java.util.stream.Collectors;

public class TypeUtils {
    /** Checks that the given type or any of its widened types matches
     * the required type. */
    public boolean checkCompatibility(ResolvedType requiredType, Set<ResolvedType> givenType, ComponentResolver componentResolver) {
        assert (requiredType != null);

        if (givenType.isEmpty()) {
            return false;
        }

        for (ResolvedType type : givenType) {
            boolean compatibleTypeFound = true;

            while (!type.getName().equals(requiredType.getName())) {
                String looserTypeName = type.getExtends();
                if (looserTypeName == null) {
                    compatibleTypeFound = false;
                    break;
                }
                type = ResolvedType.fromString(looserTypeName, componentResolver);

                if (type == null) {
                    compatibleTypeFound = false;
                    break;
                }
            }

            if (compatibleTypeFound) return true;
        }

        return false;
    }

    public static Set<ResolvedType> resolveGeneratedType(
            Generator generator,
            Map<String, Set<ResolvedType>> resolvedArguments,
            ComponentResolver componentResolver
    ) {
        // make sure we don't pass any unknown arguments

        Set<String> parameterNames = generator.getArguments().stream().map(x -> x.getName()).collect(Collectors.toSet());
        for (String argument : resolvedArguments.keySet()) {
            if (!parameterNames.contains(argument)) {
                throw new TypeError("Unknown argument for function " + generator.getName() + ": " + argument);
            }
        }

        // check types and assign type parameters

        Map<String, Set<ResolvedType>> resolvedTypeParameters = new HashMap<>();
        for (Argument parameter : generator.getArguments()) {
            String parameterName = parameter.getName();

            if (!resolvedArguments.containsKey(parameterName)) {
                if (parameter.getRequired()) {
                    throw new TypeError("Missing required argument for function " + generator.getName() + ": " + parameterName);
                }

                continue;
            }

            if (!TypeUtils.canMatchType(
                    parameter.getType(),
                    resolvedArguments.get(parameterName),
                    generator.getTypeParameters(),
                    resolvedTypeParameters,
                    componentResolver
            )) {
                throw new TypeError("Wrong argument type for function " + generator.getName() + " and argument " + parameterName);
            }

        }

        // construct return type

        String returnTypeName = generator.getGeneratedType();
        if (!returnTypeName.contains("<")) {
            // return type is not a generic
            return Set.of(ResolvedType.fromString(returnTypeName, componentResolver));
        } else {
            // return type is a generic
            return Set.of(ResolvedType.fromString(returnTypeName, resolvedTypeParameters, componentResolver));
        }
    }

    public static boolean canMatchType(
            String requiredTypeName,
            Set<ResolvedType> resolvedType,
            List<String> typeParameters,
            Map<String, Set<ResolvedType>> resolvedTypeParameters,
            ComponentResolver componentResolver
    ) {
        String requiredAtomicTypeName = requiredTypeName;
        final String[] genericTypeNames;

        if (requiredTypeName.contains("<")) {
            // this is a generic type
            requiredAtomicTypeName = requiredTypeName.substring(0, requiredTypeName.indexOf("<"));
            String requiredTypeGenerics = requiredTypeName.substring(requiredTypeName.indexOf("<") + 1, requiredTypeName.length() - 1);
            genericTypeNames = requiredTypeGenerics.split(",");
        } else {
            genericTypeNames = new String[] {};
        }

        resolvedType = filterCompatible(requiredAtomicTypeName, resolvedType, componentResolver);
        resolvedType.removeIf(x -> x.getTypeParameters().size() != genericTypeNames.length);

        boolean hasMatch = false;
        Map<String, Set<ResolvedType>> localResolvedTypeParameters = new HashMap<>();

        possibleType:
        for (ResolvedType possibleType : resolvedType) {
            if (possibleType.getTypeParameters().size() != genericTypeNames.length) continue;

            for (int i = 0; i < genericTypeNames.length; i++) {
                if (typeParameters.contains(genericTypeNames[i])) {
                    // we map the type to the type parameter

                    // TODO: add proper type narrowing
                    localResolvedTypeParameters.computeIfAbsent(genericTypeNames[i], k -> new HashSet<>()).add(possibleType);
                } else {
                    if (!canMatchType(
                            genericTypeNames[i], possibleType.getResolvedTypeParameters().get(i),
                            typeParameters, localResolvedTypeParameters, componentResolver
                    )) {
                        continue possibleType;
                    }
                }
            }

            hasMatch = true;
        }

        if (resolvedType.isEmpty() || !hasMatch) {
            return false;
        }

        resolvedTypeParameters.putAll(localResolvedTypeParameters);
        return true;
    }

    private static Set<ResolvedType> filterCompatible(String requiredAtomicTypeName, Set<ResolvedType> possibleTypes, ComponentResolver componentResolver) {
        Set<ResolvedType> filteredTypes = new HashSet<>();
        Set<ResolvedType> typesToTest = new HashSet<>(possibleTypes);

        while (!typesToTest.isEmpty()) {
            ResolvedType current = typesToTest.iterator().next();
            typesToTest.remove(current);
            if (current.getName().equals(requiredAtomicTypeName) || current.getName().startsWith(requiredAtomicTypeName + "<")) {
                filteredTypes.add(current);
            } else if (current.getExtends() != null) {
                ResolvedType extended;

                if (current.getExtends().contains("<")) {
                   extended = ResolvedType.fromString(current.getExtends(), new HashMap<>(), componentResolver);
                } else {
                   extended = ResolvedType.fromString(current.getExtends(), componentResolver);
                }

                typesToTest.add(extended);
            }
        }

        return  filteredTypes;
    }
}
