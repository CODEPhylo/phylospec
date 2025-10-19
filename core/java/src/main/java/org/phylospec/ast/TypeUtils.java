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
        for (ResolvedType parameterType : type.getParameterTypes().values()) {
            if (parameterType.getExtends() != null) return true;
        }
        return false;
    }

    public static void visitTypeAndParents(
            ResolvedType type,
            Function<ResolvedType, Boolean> visitor,
            ComponentResolver componentResolver
    ) {
        if (!visitor.apply(type)) return;
        visitParents(type, visitor, componentResolver);
    }

    public static void visitParents(
            ResolvedType type,
            Function<ResolvedType, Boolean> visitor,
            ComponentResolver componentResolver
    ) {
        if (type.getExtends() != null) {
            ResolvedType directlyExtendedType = ResolvedType.fromString(type.getExtends(), componentResolver);
            if (!visitor.apply(directlyExtendedType)) return;
            visitParents(directlyExtendedType, visitor, componentResolver);
        }

        for (final String parameterName : type.getParameterTypes().keySet()) {
            ResolvedType parameterType = type.getParameterTypes().get(parameterName);
            visitParents(
                    parameterType,
                    x -> {
                        // we replace this type param with its extended form and visit it again
                        // note that this is correct but not efficient, as we might visit
                        // the same type multiple times
                        Map<String, ResolvedType> clonedTypeParams = new HashMap<>(
                                type.getParameterTypes()
                        );
                        clonedTypeParams.put(parameterName, x);

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
        List<Argument> parameters = generator.getArguments();

        // handle edge case when a single unnamed parameter is passed

        if (resolvedArguments.size() == 1 && resolvedArguments.get(null) != null) {
            // make sure there is exactly one required parameter
            if (parameters.stream().filter(Argument::getRequired).count() != 1) {
                throw new TypeError("Missing required argument for function " + generator.getName());
            }
        }

        // make sure we don't pass any unknown arguments

        Set<String> parameterNames = parameters.stream()
                .map(Argument::getName)
                .collect(Collectors.toSet());
        for (String argument : resolvedArguments.keySet()) {
            if (!parameterNames.contains(argument) && argument != null) {
                throw new TypeError("Unknown argument for function " + generator.getName() + ": " + argument);
            }
        }

        // check passed types and resolve type parameters

        Map<String, List<ResolvedType>> possibleParameterTypeSets = new HashMap<>();
        for (Argument parameter : parameters) {
            String parameterName = parameter.getName();

            Set<ResolvedType> resolvedArgumentTypeSet = resolvedArguments.get(parameterName);

            if (resolvedArgumentTypeSet == null && parameter.getRequired()) {
                // there might be an unnamed argument
                resolvedArgumentTypeSet = resolvedArguments.get(null);
            }

            if (resolvedArgumentTypeSet == null) {
                if (parameter.getRequired()) {
                    throw new TypeError("Missing required argument for function " + generator.getName() + ": " + parameterName);
                }

                continue;
            }

            boolean foundMatch = false;
            for (ResolvedType possibleArgumentType : resolvedArgumentTypeSet) {
                if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                        parameter.getType(),
                        possibleArgumentType,
                        generator.getTypeParameters(),
                        possibleParameterTypeSets,
                        componentResolver
                )) {
                    foundMatch = true;
                }
            }

            if (!foundMatch) {
                throw new TypeError("Wrong argument type for function " + generator.getName() + " and argument " + parameterName);
            }

        }

        // find the lowest cover for every type parameter

        Map<String, Set<ResolvedType>> parameterTypeSets = new HashMap<>();
        for (String typeParameter : possibleParameterTypeSets.keySet()) {
            parameterTypeSets.put(
                    typeParameter,
                    Set.of(TypeUtils.getLowestCover(
                            possibleParameterTypeSets.get(typeParameter), componentResolver
                    ))
            );
        }


        // construct return type

        String returnTypeName = generator.getGeneratedType();
        return ResolvedType.fromString(returnTypeName, parameterTypeSets, componentResolver);
    }

    public static boolean checkAssignabilityAndResolveTypeParameters(
            String requiredTypeName,        // Distribution<K, V>
            ResolvedType resolvedType,      // RT Vector<Real, Real>
            List<String> typeParameterNames,    // T
            Map<String, List<ResolvedType>> resolvedTypeParameterTypes,
            ComponentResolver componentResolver
    ) {
        if (typeParameterNames.contains(requiredTypeName)) {
            resolvedTypeParameterTypes
                    .computeIfAbsent(requiredTypeName, x -> new ArrayList<>())
                    .add(resolvedType);
            return true;
        }

        if (!isGeneric(requiredTypeName)) {
            return covers(
                    ResolvedType.fromString(requiredTypeName, componentResolver),
                    resolvedType,
                    componentResolver);
        }

        String strippedRequiredTypeName = stripGenerics(requiredTypeName); // Vector
        List<String> requiredParameterTypeNames = parseParameterTypeNames(requiredTypeName);

        Map<String, List<ResolvedType>> localResolvedTypeParameterTypes = new HashMap<>();
        boolean[] foundMatch = new boolean[] { false };
        visitTypeAndParents(
                resolvedType,
                type -> {
                    if (!Objects.equals(type.getName(), strippedRequiredTypeName)) {
                        return true;
                    }
                    if (requiredParameterTypeNames.size() != type.getParametersNames().size()) {
                        return true;
                    }

                    boolean foundMatchForAll = true;
                    for (int i = 0; i < requiredParameterTypeNames.size(); i++) {
                        if (!checkAssignabilityAndResolveTypeParameters(
                                requiredParameterTypeNames.get(i),
                                type.getParameterTypes().get(type.getParametersNames().get(i)),
                                typeParameterNames,
                                localResolvedTypeParameterTypes,
                                componentResolver
                        )) {
                            foundMatchForAll = false;
                        }
                    }

                    if (foundMatchForAll) {
                        foundMatch[0] = true;
                        return false;
                    } else {
                        return true;
                    }
                },
                componentResolver
        );

        if (!foundMatch[0]) {
            return false;
        }

        for (String name : localResolvedTypeParameterTypes.keySet()) {
            resolvedTypeParameterTypes.computeIfAbsent(name, x -> new ArrayList<>()).addAll(
                    localResolvedTypeParameterTypes.get(name)
            );
        }

        return true;
    }

    private static List<String> parseParameterTypeNames(String typeString) {
        return Arrays.stream(typeString.substring(typeString.indexOf("<") + 1, typeString.length() - 1).split(",")).toList();
    }

    private static String stripGenerics(String typeString) {
        if (isGeneric(typeString)) {
            return typeString.substring(0, typeString.indexOf("<"));
        } else {
            return typeString;
        }
    }

    private static boolean isGeneric(String typeString) {
        return typeString.contains("<");
    }

    public static Set<ResolvedType> getLowestCoverTypeSet(List<Set<ResolvedType>> elementTypeSets, ComponentResolver componentResolver) {
        if (elementTypeSets.isEmpty()) return Set.of();

        Set<List<ResolvedType>> possibleElementTypeCombinations = new HashSet<>();
        Utils.visitCombinations(possibleElementTypeCombinations::add, elementTypeSets);

        Set<ResolvedType> lcTypeSet = new HashSet<>();
        for (List<ResolvedType> combination : possibleElementTypeCombinations) {
            ResolvedType lowestCover = getLowestCover(combination, componentResolver);
            if (lowestCover != null) lcTypeSet.add(lowestCover);
        }

        return lcTypeSet;
    }

    private static ResolvedType getLowestCover(List<ResolvedType> typeSet, ComponentResolver componentResolver) {
        if (typeSet.size() == 1) return typeSet.get(0);

        ResolvedType lowestCover = typeSet.get(0);
        for (int i = 1; i < typeSet.size(); i++) {
            lowestCover = getLowestCover(lowestCover, typeSet.get(i), componentResolver);
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
