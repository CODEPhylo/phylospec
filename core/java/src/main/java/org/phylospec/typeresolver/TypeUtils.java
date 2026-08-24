package org.phylospec.typeresolver;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.phylospec.Utils;
import org.phylospec.components.*;

public class TypeUtils {

    /**
     * Checks if some of the types in {@code assignedTypeSet} can be assigned to some of the types in {@code assigneeType}.
     * A type A can be assigned to type B if B covers A.
     */
    public static boolean canBeAssignedTo(
            ResolvedTypeSet assignedTypeSet, ResolvedTypeSet assigneeTypeSet, ComponentResolver componentResolver) {
        for (ResolvedType assignedType : assignedTypeSet) {
            for (ResolvedType assigneeType : assigneeTypeSet) {
                if (covers(assigneeType, assignedType, componentResolver)) return true;
            }
        }
        return false;
    }

    /**
     * Takes a type name (e.g. Vector), a type parameter (e.g. T) and a resolved type (e.g. a subclass of Vector<T>).
     * Returns the {@link ResolvedType} object of the type parameters. Returns null if the resolved type is not
     * actually a subtyype of the type name or the type parameter is not resolved.
     */
    public static ResolvedType recoverTypeParameter(
            String typeName, String typeParameter, ResolvedType resolvedType, ComponentResolver componentResolver) {
        ResolvedType[] recoveredType = new ResolvedType[] {null};
        visitTypeAndParents(
                resolvedType,
                t -> {
                    if (t.getName().equals(typeName)) {
                        recoveredType[0] = t;
                        return VisitorResult.STOP;
                    }
                    return VisitorResult.CONTINUE;
                },
                componentResolver);

        if (recoveredType[0] == null) {
            return null;
        } else {
            return recoveredType[0].getParameterTypes().get(typeParameter);
        }
    }

    /**
     * This function returns the typeset containing all possible return
     * types of this generator with the given resolved argument types.
     * This function takes automatically resolves type parameters using
     * the resolved arguments and uses that to build the possible return
     * types.
     */
    static ResolvedGeneratorApplication resolveGeneratedType(
            Generator generator,
            Map<String, ResolvedTypeSet> resolvedArgumentTypeSets,
            ComponentResolver componentResolver) {
        // check passed types and resolve type parameters

        Map<String, List<ResolvedType>> possibleParameterTypeSets = new HashMap<>();
        Map<String, ResolvedTypeSet> possibleArgumentTypeSets = new HashMap<>();
        for (Argument parameter : generator.getArguments()) {
            String parameterName = parameter.getName();

            ResolvedTypeSet resolvedArgumentTypeSet = resolvedArgumentTypeSets.get(parameterName);
            if (resolvedArgumentTypeSet == null) {
                continue;
            }

            // check for every possible argument type if they can be assigned to
            // the required parameter type. if yes, possibleParameterTypeSets is
            // updated with the corresponding types for type parameters

            boolean foundMatch = false;
            ResolvedTypeSet matchingArgumentTypeSet = new ResolvedTypeSet();
            for (ResolvedType possibleArgumentType : resolvedArgumentTypeSet) {
                if (TypeUtils.checkAssignabilityAndResolveTypeParameters(
                        parameter.getType(),
                        possibleArgumentType,
                        generator.getTypeParameters(),
                        possibleParameterTypeSets,
                        componentResolver)) {
                    foundMatch = true;
                    matchingArgumentTypeSet.add(possibleArgumentType);
                }
            }
            possibleArgumentTypeSets.put(parameterName, matchingArgumentTypeSet);

            if (!foundMatch) {
                throw new TypeError(
                        "Wrong argument type for function `"
                                + generator.getName()
                                + "` and argument `"
                                + parameterName
                                + "`.",
                        "You need to use a value of type '"
                                + new ParsedType(parameter.getType()).stripNamespace()
                                + "'.");
            }
        }

        // find the lowest cover for every type parameter
        // this is not the most specific way to handle this, as we ignore any
        // dependencies within different type parameters

        Map<String, ResolvedTypeSet> parameterTypeSets = new HashMap<>();
        for (String typeParameter : possibleParameterTypeSets.keySet()) {
            parameterTypeSets.put(
                    typeParameter,
                    ResolvedTypeSet.of(
                            TypeUtils.getLowestCover(possibleParameterTypeSets.get(typeParameter), componentResolver)));
        }

        // construct return type

        String returnTypeName = generator.getGeneratedType();
        return new ResolvedGeneratorApplication(
                ResolvedType.fromString(returnTypeName, parameterTypeSets, componentResolver),
                possibleArgumentTypeSets);
    }

    public record ResolvedGeneratorApplication(
            ResolvedTypeSet generatedTypeSet, Map<String, ResolvedTypeSet> resolvedArguments) {}
    ;

    /**
     * Checks if {@code query} covers {@code reference}. Type A covers type B if A = B or if A extends B.
     */
    public static boolean covers(ResolvedType query, ResolvedType reference, ComponentResolver componentResolver) {
        if (query.equals(reference)) return true;

        // test if the reference is stripped from its generics and if yes, if the stripped type
        // matches
        if (query.getParameterTypes().isEmpty() && query.getTypeComponent().equals(reference.getTypeComponent()))
            return true;

        boolean[] covers = {false};
        visitParents(
                reference,
                x -> {
                    if (x.equals(query)) {
                        covers[0] = true;
                        return VisitorResult.STOP;
                    }
                    return VisitorResult.CONTINUE;
                },
                componentResolver);
        return covers[0];
    }

    /**
     * Returns a set containing the lowest cover for every possible combinations
     * of types in {@code typeSets}.
     * This function build every combination by taking one type out of every set in
     * {@code typeSets}. Then, for every such combination, the lowest cover type is
     * determined. Then the set of all lowest covers is returned.
     */
    static ResolvedTypeSet getLowestCoverTypeSet(List<ResolvedTypeSet> typeSets, ComponentResolver componentResolver) {
        if (typeSets.isEmpty()) return ResolvedTypeSet.empty();

        // we first remove duplicate type sets as this can quickly turn into a combinatorial
        // explosion
        typeSets = typeSets.stream().distinct().collect(Collectors.toList());

        Set<List<ResolvedType>> possibleElementTypeCombinations = new HashSet<>();
        Utils.visitCombinations(typeSets, possibleElementTypeCombinations::add);

        ResolvedTypeSet lcTypeSet = new ResolvedTypeSet();
        for (List<ResolvedType> combination : possibleElementTypeCombinations) {
            ResolvedType lowestCover = getLowestCover(combination, componentResolver);
            if (lowestCover != null) lcTypeSet.add(lowestCover);
        }

        return lcTypeSet;
    }

    /**
     * Returns the lowest cover of all types in the {@code typeSet}. Returns null
     * if no such cover exists.
     * A type C is the lowest cover of a typeset T if it covers all types in T,
     * and if all other covers of T cover C.
     */
    public static ResolvedType getLowestCover(List<ResolvedType> typeSet, ComponentResolver componentResolver) {
        if (typeSet.size() == 1) return typeSet.getFirst();

        ResolvedType lowestCover = typeSet.getFirst();
        for (int i = 1; i < typeSet.size(); i++) {
            lowestCover = getLowestCover(lowestCover, typeSet.get(i), componentResolver);
            if (lowestCover == null) return null;
        }

        return lowestCover;
    }

    /**
     * Returns the lowest cover of {@code type1} and {@code type2}. Returns null
     * if no such cover exists.
     * A type C is the lowest cover of type A and type B if it covers both A and B,
     * and if all other covers of A and B cover C.
     */
    static ResolvedType getLowestCover(ResolvedType type1, ResolvedType type2, ComponentResolver componentResolver) {
        ResolvedTypeSet parents1 = new ResolvedTypeSet();
        visitTypeAndParents(
                type1,
                x -> {
                    parents1.add(x);
                    return VisitorResult.CONTINUE;
                },
                componentResolver);

        ResolvedType[] lowestCover = {null};
        visitTypeAndParents(
                type2,
                x -> {
                    if (parents1.contains(x)) {
                        if (lowestCover[0] == null) {
                            lowestCover[0] = ResolvedTypeSet.merge(parents1.get(x), x);
                        }
                        return VisitorResult.STOP;
                    }
                    return VisitorResult.CONTINUE;
                },
                componentResolver);

        if (lowestCover[0] == null) {
            return null;
        }

        return lowestCover[0];
    }

    /**
     * Calls the visitor function on the type and every parent type.
     */
    public static void visitTypeAndParents(
            ResolvedType type, Function<ResolvedType, VisitorResult> visitor, ComponentResolver componentResolver) {
        if (visitor.apply(type) == VisitorResult.STOP) return;
        visitParents(type, visitor, componentResolver);
    }

    /**
     * Calls the visitor function on the type and every parent and aliased type.
     */
    public static void visitParents(
            ResolvedType type, Function<ResolvedType, VisitorResult> visitor, ComponentResolver componentResolver) {
        // visit aliases

        if (type.getAlias() != null) {
            HashMap<String, ResolvedTypeSet> aliasedTypeParameters = new HashMap<>();
            for (String name : type.getParameterTypes().keySet()) {
                aliasedTypeParameters.put(
                        name, ResolvedTypeSet.of(type.getParameterTypes().get(name)));
            }

            ResolvedTypeSet aliasedTypeSet =
                    ResolvedType.fromString(type.getAlias(), aliasedTypeParameters, componentResolver, false);
            for (ResolvedType aliasedType : aliasedTypeSet) {
                aliasedType.properties().attach(type.properties());
                visitTypeAndParents(aliasedType, visitor, componentResolver);
            }
        }

        // visit direct parents

        if (type.getExtends() != null) {
            HashMap<String, ResolvedTypeSet> inheritedTypeParameters = new HashMap<>();
            for (String name : type.getParameterTypes().keySet()) {
                inheritedTypeParameters.put(
                        name, ResolvedTypeSet.of(type.getParameterTypes().get(name)));
            }

            ResolvedTypeSet directlyExtendedTypeSet =
                    ResolvedType.fromString(type.getExtends(), inheritedTypeParameters, componentResolver, false);
            for (ResolvedType directlyExtendedType : directlyExtendedTypeSet) {
                directlyExtendedType.properties().attach(type.properties());
                visitTypeAndParents(directlyExtendedType, visitor, componentResolver);
            }
        }

        // visit parents of the type parameter (we assume covariance everywhere)

        for (final String parameterName : type.getParameterTypes().keySet()) {
            ResolvedType parameterType = type.getParameterTypes().get(parameterName);
            visitParents(
                    parameterType,
                    x -> {
                        // we replace this type param with its extended form and visit it again
                        // note that this is correct but not efficient, as we might visit
                        // the same type multiple times
                        Map<String, ResolvedType> clonedTypeParams = new HashMap<>(type.getParameterTypes());
                        clonedTypeParams.put(parameterName, x);

                        ResolvedType clonedType = new ResolvedType(type.getTypeComponent(), clonedTypeParams);
                        clonedType.properties().attach(type.properties());
                        if (visitor.apply(clonedType) == VisitorResult.STOP) return VisitorResult.STOP;

                        visitParents(clonedType, visitor, componentResolver);

                        return VisitorResult.CONTINUE;
                    },
                    componentResolver);
        }
    }

    /**
     * This function checks if an object of {@code requiredTypeName} (e.g. {@code "Vector<T>"})
     * can be assigned to an argument of type {@code resolvedType} (e.g. {@code "Vector<Real>"}).
     * If this is the case, the passed {@code resolvedTypeParameterTypes} will be updated with the
     * matching type parameter (e.g. T -> Real).
     *
     * @param requiredTypeName           the type name of the argument
     * @param resolvedType               the resolved type of the object to be assigned to the argument
     * @param typeParameterNames         the names of the type parameters of the generator
     * @param resolvedTypeParameterTypes the dict with type parameter types, will be updated if the argument matches
     * @param componentResolver          the component resolver
     * @return true if the object can be assigned to the argument
     */
    public static boolean checkAssignabilityAndResolveTypeParameters(
            String requiredTypeName,
            ResolvedType resolvedType,
            List<String> typeParameterNames,
            Map<String, List<ResolvedType>> resolvedTypeParameterTypes,
            ComponentResolver componentResolver) {
        if (typeParameterNames.contains(requiredTypeName)) {
            // requiredTypeName is simply a type parameter (e.g. "T")
            // we add the resolved type to the possible resolved types of the type parameter
            resolvedTypeParameterTypes
                    .computeIfAbsent(requiredTypeName, x -> new ArrayList<>())
                    .add(resolvedType);
            return true;
        }

        ParsedType parsedRequiredType = new ParsedType(requiredTypeName);

        if (!parsedRequiredType.isGeneric()) {
            ResolvedTypeSet requiredTypeSet = ResolvedType.fromString(requiredTypeName, componentResolver, true);

            for (ResolvedType requiredType : requiredTypeSet) {
                if (covers(requiredType, resolvedType, componentResolver)) {
                    return true;
                }
            }

            return false;
        }

        Type requiredTypeComponent = componentResolver.resolveType(requiredTypeName);
        List<ParsedType> requiredParameterTypeNames = parsedRequiredType.getTypeParameters();

        // we look at all parents of resolvedType to find the type matching the given
        // requiredTypeName

        // we don't want to update the type parameter map until we are sure that everything matches
        Map<String, List<ResolvedType>> localResolvedTypeParameterTypes = new HashMap<>();

        boolean[] foundMatch = new boolean[] {false};
        visitTypeAndParents(
                resolvedType,
                type -> {
                    if (!Objects.equals(type.getName(), requiredTypeComponent.getName())) {
                        return VisitorResult.CONTINUE;
                    }
                    if (requiredParameterTypeNames.size()
                            != type.getParametersNames().size()) {
                        return VisitorResult.CONTINUE;
                    }

                    // the atomic type matches, let's recursively check all type parameters

                    boolean foundMatchForAll = true;
                    for (int i = 0; i < requiredParameterTypeNames.size(); i++) {
                        if (!checkAssignabilityAndResolveTypeParameters(
                                requiredParameterTypeNames.get(i).getTypeString(),
                                type.getParameterTypes()
                                        .get(type.getParametersNames().get(i)),
                                typeParameterNames,
                                localResolvedTypeParameterTypes,
                                componentResolver)) {
                            foundMatchForAll = false;
                        }
                    }

                    if (foundMatchForAll) {
                        // all type parameters match as well
                        foundMatch[0] = true;
                        return VisitorResult.STOP;
                    } else {
                        return VisitorResult.CONTINUE;
                    }
                },
                componentResolver);

        if (!foundMatch[0]) {
            return false;
        }

        // the entire type matches, we update the type parameter map

        for (String name : localResolvedTypeParameterTypes.keySet()) {
            resolvedTypeParameterTypes
                    .computeIfAbsent(name, x -> new ArrayList<>())
                    .addAll(localResolvedTypeParameterTypes.get(name));
        }

        return true;
    }

    public enum VisitorResult {
        STOP,
        CONTINUE
    }
}
