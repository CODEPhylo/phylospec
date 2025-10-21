package org.phylospec.typeresolver;

import org.phylospec.Utils;
import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class TypeUtils {

    /**
     * Checks if any of the types in {@code assignedTypeSet} can be assigned to {@code assigneeType}.
     * A type A can be assigned to type B if B covers A.
     */
    static boolean canBeAssignedTo(Set<ResolvedType> assignedTypeSet, ResolvedType assigneeType, ComponentResolver componentResolver) {
        for (ResolvedType assignedType : assignedTypeSet) {
            if (covers(assigneeType, assignedType, componentResolver)) return true;
        }
        return false;
    }

    /** This function returns the typeset containing all possible return
     * types of this generator with the given resolved argument types.
     * This function takes automatically resolves type parameters using
     * the resolved arguments and uses that to build the possible return
     * types. */
    static Set<ResolvedType> resolveGeneratedType(
            Generator generator,
            Map<String, Set<ResolvedType>> resolvedArguments,
            ComponentResolver componentResolver
    ) {
        List<Argument> parameters = generator.getArguments();

        // handle edge case when a single unnamed parameter is passed

        if (resolvedArguments.size() == 1 && resolvedArguments.get(null) != null) {
            // make sure there is exactly one required parameter
            if (parameters.stream().filter(Argument::getRequired).count() != 1) {
                throw new TypeError("Missing required argument for function `" + generator.getName() + "`");
            }
        }

        // make sure we don't pass any unknown arguments

        Set<String> parameterNames = parameters.stream()
                .map(Argument::getName)
                .collect(Collectors.toSet());
        for (String argument : resolvedArguments.keySet()) {
            if (!parameterNames.contains(argument) && argument != null) {
                throw new TypeError("Function `" + generator.getName() + "` takes no argument named `" + argument + "`");
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
                    throw new TypeError("Function `" + generator.getName() + "` takes the required argument `" + parameterName + "`");
                }

                continue;
            }

            // check for every possible argument type if they can be assigned to
            // the required parameter type. if yes, possibleParameterTypeSets is
            // updated with the corresponding types for type parameters

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
                throw new TypeError("Wrong argument type for function `" + generator.getName() + "` and argument `" + parameterName + "`");
            }

        }

        // find the lowest cover for every type parameter
        // this is not the most specific way to handle this, as we ignore any
        // dependencies within different type parameters

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

    /** Checks whether the given type String (e.g. {@code "Vector<Real>"}) is a generic. */
    static boolean isGeneric(String typeString) {
        return typeString.contains("<");
    }

    /** Strips the generic part of the type name (e.g. {@code "Vector<Real>"} to {@code "Vector"}). */
    static String stripGenerics(String typeString) {
        if (isGeneric(typeString)) {
            return typeString.substring(0, typeString.indexOf("<"));
        } else {
            return typeString;
        }
    }

    /** Returns a list containing the type strings of the generic type parameters. */
    static List<String> parseParameterTypeNames(String typeString) {
        if (isGeneric(typeString))
            return Arrays.stream(typeString.substring(typeString.indexOf("<") + 1, typeString.length() - 1).split(",")).toList();
        else
            return List.of();
    }

    /** Checks if {@code query} covers {@code reference}. Type A covers type B if A = B or if A extends B. */
    static boolean covers(ResolvedType query, ResolvedType reference, ComponentResolver componentResolver) {
        if (query.equals(reference)) return true;

        boolean[] covers = {false};
        visitParents(
                reference, x -> {
                    if (x.equals(query)) {
                        covers[0] = true;
                        return Visitor.STOP;
                    }
                    return Visitor.CONTINUE;
                }, componentResolver
        );
        return covers[0];
    }

    /** Returns a set containing the lowest cover for every possible combinations
     * of types in {@code typeSets}.
     * This function build every combination by taking one type out of every set in
     * {@code typeSets}. Then, for every such combination, the lowest cover type is
     * determined. Then the set of all lowest covers is returned. */
    static Set<ResolvedType> getLowestCoverTypeSet(List<Set<ResolvedType>> typeSets, ComponentResolver componentResolver) {
        if (typeSets.isEmpty()) return Set.of();

        Set<List<ResolvedType>> possibleElementTypeCombinations = new HashSet<>();
        Utils.visitCombinations(typeSets, possibleElementTypeCombinations::add);

        Set<ResolvedType> lcTypeSet = new HashSet<>();
        for (List<ResolvedType> combination : possibleElementTypeCombinations) {
            ResolvedType lowestCover = getLowestCover(combination, componentResolver);
            if (lowestCover != null) lcTypeSet.add(lowestCover);
        }

        return lcTypeSet;
    }

    /** Returns the lowest cover of all types in the {@code typeSet}. Returns null
     * if no such cover exists.
     * A type C is the lowest cover of a typeset T if it covers all types in T,
     * and if all other covers of T cover C. */
    static ResolvedType getLowestCover(List<ResolvedType> typeSet, ComponentResolver componentResolver) {
        if (typeSet.size() == 1) return typeSet.get(0);

        ResolvedType lowestCover = typeSet.get(0);
        for (int i = 1; i < typeSet.size(); i++) {
            lowestCover = getLowestCover(lowestCover, typeSet.get(i), componentResolver);
            if (lowestCover == null) return null;
        }

        return lowestCover;
    }

    /** Returns the lowest cover of {@code type1} and {@code type2}. Returns null
     * if no such cover exists.
     * A type C is the lowest cover of type A and type B if it covers both A and B,
     * and if all other covers of A and B cover C. */
    static ResolvedType getLowestCover(ResolvedType type1, ResolvedType type2, ComponentResolver componentResolver) {
        if (type1.equals(type2)) return type1;

        Set<ResolvedType> parents1 = new HashSet<>();
        visitParents(type1, x -> {
            parents1.add(x);
            return Visitor.CONTINUE;
        }, componentResolver);

        ResolvedType[] lowestCover = {null};
        visitParents(type2, x -> {
            if (parents1.contains(x)) {
                lowestCover[0] = x;
                return Visitor.STOP;
            }
            return Visitor.CONTINUE;
        }, componentResolver);

        return lowestCover[0];
    }

    /** Calls the visitor function on the type and every parent type. */
    static void visitTypeAndParents(
            ResolvedType type,
            Function<ResolvedType, Visitor> visitor,
            ComponentResolver componentResolver
    ) {
        if (visitor.apply(type) == Visitor.STOP) return;
        visitParents(type, visitor, componentResolver);
    }

    /** Calls the visitor function on the type and every parent type. */
    static void visitParents(
            ResolvedType type,
            Function<ResolvedType, Visitor> visitor,
            ComponentResolver componentResolver
    ) {
        if (type.getExtends() != null) {
            ResolvedType directlyExtendedType = ResolvedType.fromString(type.getExtends(), componentResolver);
            visitTypeAndParents(directlyExtendedType, visitor, componentResolver);
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
                        if (visitor.apply(clonedType) == Visitor.STOP) return Visitor.STOP;

                        visitParents(
                                clonedType, visitor, componentResolver
                        );

                        return Visitor.CONTINUE;
                    },
                    componentResolver
            );
        }
    }

    private static boolean checkAssignabilityAndResolveTypeParameters(
            String requiredTypeName,
            ResolvedType resolvedType,
            List<String> typeParameterNames,
            Map<String, List<ResolvedType>> resolvedTypeParameterTypes,
            ComponentResolver componentResolver
    ) {
        if (typeParameterNames.contains(requiredTypeName)) {
            // requiredTypeName is simply a type parameter (e.g. "T")
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

        String strippedRequiredTypeName = stripGenerics(requiredTypeName);
        List<String> requiredParameterTypeNames = parseParameterTypeNames(requiredTypeName);

        // we look at all parents of resolvedType to find the type matching the given requiredTypeName

        // we don't want to update the type parameter map until we are sure that everything matches
        Map<String, List<ResolvedType>> localResolvedTypeParameterTypes = new HashMap<>();

        boolean[] foundMatch = new boolean[] { false };
        visitTypeAndParents(
                resolvedType,
                type -> {
                    if (!Objects.equals(type.getName(), strippedRequiredTypeName)) {
                        return Visitor.CONTINUE;
                    }
                    if (requiredParameterTypeNames.size() != type.getParametersNames().size()) {
                        return Visitor.CONTINUE;
                    }

                    // the atomic type matches, let's recursively check all type parameters

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
                        // all type parameters match as well
                        foundMatch[0] = true;
                        return Visitor.STOP;
                    } else {
                        return Visitor.CONTINUE;
                    }
                },
                componentResolver
        );

        if (!foundMatch[0]) {
            return false;
        }

        // the entire type matches, we update the type parameter map

        for (String name : localResolvedTypeParameterTypes.keySet()) {
            resolvedTypeParameterTypes.computeIfAbsent(name, x -> new ArrayList<>()).addAll(
                    localResolvedTypeParameterTypes.get(name)
            );
        }

        return true;
    }

    enum Visitor {
        STOP,
        CONTINUE
    }
}
