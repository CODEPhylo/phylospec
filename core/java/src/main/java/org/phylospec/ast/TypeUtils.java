package org.phylospec.ast;

import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TypeUtils {

    public static Set<ResolvedType> findUnion(Set<ResolvedType> typeSet1, Set<ResolvedType> typeSet2, ComponentResolver componentResolver) {
        Set<ResolvedType> union = new HashSet<>();
        union.addAll(typeSet1);
        union.addAll(typeSet2);

        for (Iterator<ResolvedType> it = union.iterator(); it.hasNext(); ) {
            ResolvedType type = it.next();
            // we go up the type hierarchy for type T. if we come across a type already in
            // the union, we can remove T from the union, as it is already covered

            // walk up the hierarchy
            ResolvedType currentType = type;
            visitExtendedTypes(currentType, x -> {
                if (union.contains(x)) {
                    it.remove();
                }
            }, componentResolver);
        }

        return union;
    }

    public static boolean partiallyCoversTypeSet(ResolvedType query, Set<ResolvedType> refTypeSet, ComponentResolver componentResolver) {
        for (ResolvedType ref : refTypeSet) {
            if (partiallyCoversTypeSet(query, ref, componentResolver)) {
                return true;
            }
        }
        return false;
    }

    public static boolean partiallyCoversTypeSet(ResolvedType query, ResolvedType ref, ComponentResolver componentResolver) {
        if (query.equals(ref)) return true;

        final boolean[] coversRevType = { false }; // use a mutable wrapper that we can change it in the lambda
        visitExtendedTypes(ref, x -> {
            if (partiallyCoversType(query, x, componentResolver)) coversRevType[0] = true;
        }, componentResolver);
        return coversRevType[0];
    }

    private static boolean partiallyCoversType(ResolvedType query, ResolvedType ref, ComponentResolver componentResolver) {
        if (!query.getTypeComponent().equals(ref.getTypeComponent())) return false;
        if (query.getResolvedTypeParameters().size() != ref.getResolvedTypeParameters().size()) return false;
        for (int i = 0; i < query.getResolvedTypeParameters().size(); i++) {
            assert query.getResolvedTypeParameters().get(i).size() == 1;
            if (!partiallyCoversTypeSet(
                    query.getResolvedTypeParameters().get(i).iterator().next(),
                    ref.getResolvedTypeParameters().get(i),
                    componentResolver
            )) return false;
        }

        return true;
    }

    public static boolean coversTypeSet(ResolvedType query, Set<ResolvedType> refTypeSet, ComponentResolver componentResolver) {
        for (ResolvedType ref : refTypeSet) {
            if (!coversType(query, ref, componentResolver)) {
                return false;
            }
        }
        return true;
    }

    public static boolean coversType(ResolvedType query, ResolvedType ref, ComponentResolver componentResolver) {
        if (query.equals(ref)) return true;

        final boolean[] coversRevType = { false }; // use a mutable wrapper that we can change it in the lambda
        visitExtendedTypes(ref, x -> {
            if (query.equals(x)) coversRevType[0] = true;
        }, componentResolver);
        return coversRevType[0];
    }

    private static void visitExtendedTypes(ResolvedType type, Consumer<ResolvedType> visitor, ComponentResolver componentResolver) {
        visitExtendedTypes(type, visitor, new HashMap<>(), componentResolver);
    }

    private static void visitExtendedTypes(
            ResolvedType type,
            Consumer<ResolvedType> visitor,
            Map<String, Set<ResolvedType>> typeParameters,
            ComponentResolver componentResolver
    ) {
        if (type.getExtends() != null) {
            ResolvedType directlyExtendedType = ResolvedType.fromString(type.getExtends(), typeParameters, componentResolver);
            visitor.accept(directlyExtendedType);
            visitExtendedTypes(directlyExtendedType, visitor, typeParameters, componentResolver);
        }

        for (int i = 0; i < type.getResolvedTypeParameters().size(); i++) {
            final int j = i;
            Set<ResolvedType> typeParamTypeSet = type.getResolvedTypeParameters().get(i);
            for (ResolvedType t : typeParamTypeSet) {
                visitExtendedTypes(
                        t,
                        x -> {
                            // we replace this type param with its extended form and visit it again
                            // note that this is correct but not efficient, as we might visit
                            // the same type multiple times
                            Set<ResolvedType> clonedTypeParamTypeSet = new HashSet<>(typeParamTypeSet);
                            clonedTypeParamTypeSet.remove(t);
                            clonedTypeParamTypeSet.add(x);

                            List<Set<ResolvedType>> clonedTypeParams = new ArrayList<>(
                                    type.getResolvedTypeParameters()
                            );
                            clonedTypeParams.set(j, clonedTypeParamTypeSet);

                            ResolvedType clonedType = new ResolvedType(type.getTypeComponent(), clonedTypeParams);
                            visitor.accept(clonedType);

                            visitExtendedTypes(
                                    clonedType, visitor, typeParameters, componentResolver
                            );
                        },
                        typeParameters,
                        componentResolver
                );
            }
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
            genericTypeNames = new String[]{};
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
                    localResolvedTypeParameters.computeIfAbsent(genericTypeNames[i], k -> new HashSet<>()).addAll(possibleType.getResolvedTypeParameters().get(i));
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
                ResolvedType extended;

                if (current.getExtends().contains("<")) {
                    extended = ResolvedType.fromString(current.getExtends(), new HashMap<>(), componentResolver);
                } else {
                    extended = ResolvedType.fromString(current.getExtends(), componentResolver);
                }

                typesToTest.add(extended);
            }
        }

        return filteredTypes;
    }

}
