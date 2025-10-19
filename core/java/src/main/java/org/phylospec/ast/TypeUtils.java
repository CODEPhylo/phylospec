package org.phylospec.ast;

import org.phylospec.Utils;
import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.components.Type;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TypeUtils {

    public static boolean canBeAssignedTo(Set<ResolvedType> assignedTypeSet, ResolvedType assigneeType, ComponentResolver componentResolver) {
        for (ResolvedType assignedType : assignedTypeSet) {
            if (covers(assigneeType, assignedType, componentResolver)) return true;
        }
        return false;
    }

    public static boolean covers(ResolvedType query, ResolvedType reference, ComponentResolver componentResolver) {
        if (query.equals(reference)) return true;

        boolean[] covers = {false};
        visitParents(
                reference, x -> {
                    if (x.equals(query)) {
                        covers[0] = true;
                        return false; // we end the recursion
                    }
                    return true; // we continue the recursion
                }, componentResolver
        );
        return covers[0];
    }

    public static boolean hasParents(ResolvedType type) {
        if (type.getTypeComponent().getExtends() != null) return true;
        for (ResolvedType parameterType : type.getParameterTypes()) {
            if (parameterType.getExtends() != null) return true;
        }
        return false;
    }

    private static void visitParents(
            ResolvedType type,
            Function<ResolvedType, Boolean> visitor,
            ComponentResolver componentResolver
    ) {
        if (type.getExtends() != null) {
            ResolvedType directlyExtendedType = ResolvedType.fromString(type.getExtends(), componentResolver);
            if (!visitor.apply(directlyExtendedType)) return;
            visitParents(directlyExtendedType, visitor, componentResolver);
        }

        for (int i = 0; i < type.getParameterTypes().size(); i++) {
            final int j = i;
            ResolvedType parameterType = type.getParameterTypes().get(i);
            visitParents(
                    parameterType,
                    x -> {
                        // we replace this type param with its extended form and visit it again
                        // note that this is correct but not efficient, as we might visit
                        // the same type multiple times
                        List<ResolvedType> clonedTypeParams = new ArrayList<>(
                                type.getParameterTypes()
                        );
                        clonedTypeParams.set(j, x);

                        ResolvedType clonedType = new ResolvedType(type.getTypeComponent(), clonedTypeParams);
                        if (!visitor.apply(clonedType)) return false;

                        visitParents(
                                clonedType, visitor, componentResolver
                        );

                        return true;
                    },
                    componentResolver
            );
        }
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

        // check passed types and assign type parameters

        Map<String, Set<ResolvedType>> resolvedTypeParameters = new HashMap<>();
        for (Argument parameter : generator.getArguments()) {
            String parameterName = parameter.getName();

            if (!resolvedArguments.containsKey(parameterName)) {
                if (parameter.getRequired()) {
                    throw new TypeError("Missing required argument for function " + generator.getName() + ": " + parameterName);
                }

                continue;
            }

            if (!TypeUtils.checkAssignabilityAndBindTypeParameters(
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
        return ResolvedType.fromString(returnTypeName, resolvedTypeParameters, componentResolver);
    }

    public static boolean checkAssignabilityAndBindTypeParameters(
            String requiredTypeName,
            Set<ResolvedType> resolvedTypeSet,
            List<String> typeParameters,
            Map<String, Set<ResolvedType>> resolvedParameterTypeSets,
            ComponentResolver componentResolver
    ) {
        String requiredAtomicTypeName = requiredTypeName;
        final String[] genericTypeNames;

        // parse type name

        if (requiredTypeName.contains("<")) {
            // this is a generic type
            requiredAtomicTypeName = requiredTypeName.substring(0, requiredTypeName.indexOf("<"));
            String requiredTypeGenerics = requiredTypeName.substring(requiredTypeName.indexOf("<") + 1, requiredTypeName.length() - 1);
            genericTypeNames = requiredTypeGenerics.split(",");
        } else {
            genericTypeNames = new String[]{};
        }

        resolvedTypeSet = filterCompatible(requiredAtomicTypeName, resolvedTypeSet, componentResolver);
        resolvedTypeSet.removeIf(x -> x.getParametersNames().size() != genericTypeNames.length);

        boolean hasMatch = false;
        Map<String, Set<ResolvedType>> localResolvedTypeParameters = new HashMap<>();

        possibleType:
        for (ResolvedType possibleType : resolvedTypeSet) {
            if (possibleType.getParametersNames().size() != genericTypeNames.length) continue;

            for (int i = 0; i < genericTypeNames.length; i++) {
                if (typeParameters.contains(genericTypeNames[i])) {
                    // we map the type to the type parameter

                    // TODO: add proper type narrowing
                    localResolvedTypeParameters.computeIfAbsent(genericTypeNames[i], k -> new HashSet<>()).add(
                            possibleType.getParameterTypes().get(i)
                    );
                } else {
                    if (!checkAssignabilityAndBindTypeParameters(
                            genericTypeNames[i], Set.of(possibleType.getParameterTypes().get(i)),
                            typeParameters, localResolvedTypeParameters, componentResolver
                    )) {
                        continue possibleType;
                    }
                }
            }

            hasMatch = true;
        }

        if (resolvedTypeSet.isEmpty() || !hasMatch) {
            return false;
        }

        resolvedParameterTypeSets.putAll(localResolvedTypeParameters);
        return true;
    }

    private static Set<ResolvedType> filterCompatible(String
                                                              requiredAtomicTypeName, Set<ResolvedType> possibleTypes, ComponentResolver componentResolver) {
        Set<ResolvedType> filteredTypes = new HashSet<>();
        Set<ResolvedType> typesToTest = new HashSet<>(possibleTypes);

        while (!typesToTest.isEmpty()) {
            ResolvedType current = typesToTest.iterator().next();
            typesToTest.remove(current);
            if (current.getName().equals(requiredAtomicTypeName) || current.getName().startsWith(requiredAtomicTypeName + "<")) {
                filteredTypes.add(current);
            } else if (current.getExtends() != null) {
                ResolvedType extended = ResolvedType.fromString(current.getExtends(), componentResolver);
                typesToTest.add(extended);
            }
        }

        return filteredTypes;
    }

    public static Set<ResolvedType> inferArrayType(List<Set<ResolvedType>> elementTypeSets, ComponentResolver componentResolver) {
        if (elementTypeSets.size() == 0) return Set.of();

        Set<List<ResolvedType>> possibleElementTypeCombinations = new HashSet<>();
        Utils.visitCombinations(possibleElementTypeCombinations::add, elementTypeSets);

        Set<ResolvedType> elementTypeSet = new HashSet<>();
        for (List<ResolvedType> combination : possibleElementTypeCombinations) {
            ResolvedType lowestCover = getLowestCover(combination, componentResolver);
            if (lowestCover != null) elementTypeSet.add(lowestCover);
        }

        Type vectorComponent = componentResolver.resolveType("Vector");
        Set<ResolvedType> arrayTypeSet = elementTypeSet.stream().map(
                x -> new ResolvedType(vectorComponent, List.of(x))
        ).collect(Collectors.toSet());
        return arrayTypeSet;
    }

    private static ResolvedType getLowestCover(List<ResolvedType> combination, ComponentResolver componentResolver) {
        if (combination.size() == 1) return combination.get(0);

        ResolvedType lowestCover = combination.get(0);
        for (int i = 1; i < combination.size(); i++) {
            lowestCover = getLowestCover(lowestCover, combination.get(i), componentResolver);
            if (lowestCover == null) return null;
        }

        return lowestCover;
    }

    private static ResolvedType getLowestCover(ResolvedType type1, ResolvedType type2, ComponentResolver componentResolver) {
        if (type1.equals(type2)) return type1;

        Set<ResolvedType> parents1 = new HashSet<>();
        visitParents(type1, x -> {
            parents1.add(x);
            return true;
        }, componentResolver);

        ResolvedType[] lowestCover = {null};
        visitParents(type2, x -> {
            if (parents1.contains(x)) {
                lowestCover[0] = x;
                return false;
            }
            return true;
        }, componentResolver);

        return lowestCover[0];
    }
}
