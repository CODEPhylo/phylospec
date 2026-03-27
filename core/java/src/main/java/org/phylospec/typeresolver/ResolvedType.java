package org.phylospec.typeresolver;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

import java.util.*;

import static org.phylospec.Utils.visitCombinations;

/// This class represents a fully resolved type.
///
/// Each resolved type has a reference to the corresponding type component
/// and a map binding the generic type parameters to other resolved types.
public class ResolvedType {
    ResolvedType(Type typeComponent, Map<String, ResolvedType> parameterTypes) {
        this.typeComponent = typeComponent;
        this.parameterTypes = parameterTypes;
    }

    private final Map<String, ResolvedType> parameterTypes;
    private final Type typeComponent;

    public Type getTypeComponent() {
        return typeComponent;
    }

    public String getName() {
        return typeComponent.getName();
    }

    public String getShortName() {
        String[] splitNamespace = typeComponent.getName().split("\\.");
        return splitNamespace[splitNamespace.length - 1];
    }

    public String getExtends() {
        return typeComponent.getExtends();
    }

    public Map<String, ResolvedType> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParametersNames() {
        return typeComponent.getTypeParameters();
    }

    /**
     * Creates a {@link ResolvedType} object based on the type name. Note that the given type must not
     * be a generic type, and it must have been imported in the given {@link ComponentResolver}.
     */
    public static ResolvedType fromString(String typeString, ComponentResolver componentResolver) {
        return ResolvedType.fromString(typeString, new HashMap<>(), componentResolver).iterator().next();
    }

    /**
     * Creates a {@link ResolvedType} object based on the type name and the given resolved type parameters in
     * correct order.
     * Note that the given type must have been imported in the given {@link ComponentResolver}.
     */
    public static Set<ResolvedType> fromString(String typeString, List<Set<ResolvedType>> typeParameters, ComponentResolver componentResolver) {
        // we build a map of the type parameters and then use the more general overloaded method

        String atomicTypeString = TypeUtils.stripGenerics(typeString);
        Type typeComponent = componentResolver.resolveType(atomicTypeString);
        if (typeComponent == null) {
            throw new TypeError(
                    "The type '" + typeString + "' does not exist.",
                    "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?"
            );
        }

        if (typeParameters.size() != typeComponent.getTypeParameters().size()) {
            throw new TypeError(
                    "The type '" + typeString + "' takes " + typeComponent.getTypeParameters().size() + " type parameters, but you provided " + typeParameters.size() + ".",
                    "Provide exactly " + typeParameters.size() + " type parameters."
            );
        }

        Map<String, Set<ResolvedType>> typeParameterMap = new HashMap<>();
        for (int i = 0; i < typeParameters.size(); i++) {
            typeParameterMap.put(typeComponent.getTypeParameters().get(i), typeParameters.get(i));
        }

        return ResolvedType.fromString(typeString, typeParameterMap, componentResolver);
    }

    /// Returns all [ResolvedType] objects that can be created based on the type name and the
    /// type parameter map. Note that the type must have been imported in the given [ComponentResolver].
    ///
    /// This method returns a set of [ResolvedType] objects because you can pass type sets for
    /// the type parameters. As an example, {@code Vector<T>} with {@code T = [Real, Integer]} will
    /// return {@code [Vector<Real>, Vector<Integer>]}.
    public static Set<ResolvedType> fromString(String typeString, Map<String, Set<ResolvedType>> typeParameters, ComponentResolver componentResolver) {
        String atomicTypeString = TypeUtils.stripGenerics(typeString);
        Type typeComponent = componentResolver.resolveType(atomicTypeString);
        if (typeComponent == null) {
            throw new TypeError(
                    "The type '" + typeString + "' does not exist.",
                    "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?"
            );
        }

        if (typeComponent.getAlias() != null) {
            // this has an alias
            // we resolve the aliased type
            return ResolvedType.fromString(typeComponent.getAlias(), typeParameters, componentResolver);
        }

        // resolve the possible type parameters

        // we have two cases:
        // either, the type name is given as a generic ("Vector<Real>"), and we resolve the type parameters for the given type names
        // or it is not given as a generic ("Vector"). in that case, it still might actually be a generic type, and we can use the
        // given typeParameters to resolve the parameters

        List<String> typeParameterNames = TypeUtils.parseParameterTypes(typeString);
        List<Set<ResolvedType>> inferredTypeParameters = new ArrayList<>();

        if (TypeUtils.isGeneric(typeString)) {
            // in this case, the given type string directly indicates the type parameters (e.g. Vector<Real>).
            // we resolve the type parameters from the string

            for (String typeParameterName : typeParameterNames) {
                if (typeComponent.getTypeParameters().contains(typeParameterName) && typeParameters.containsKey(typeParameterName)) {
                    // parameter type is a Generic (like T) and we know its value
                    inferredTypeParameters.add(typeParameters.get(typeParameterName));
                } else {
                    // parameter type is another type (like Real) and we resolve it recursively
                    inferredTypeParameters.add(
                            ResolvedType.fromString(typeParameterName, typeParameters, componentResolver)
                    );
                }
            }
        } else {
            // in this case, the type string has no indication of type parameters
            // however, the type might still be a generic one, and we got the resolved parameter types through the
            // passed 'typeParameters'

            for (String typeParameterName : typeComponent.getTypeParameters()) {
                if (!typeParameters.containsKey(typeParameterName)) {
                    throw new TypeError(
                            "The type '" + typeString + "' does not exist.",
                            "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?"
                    );
                }
                inferredTypeParameters.add(typeParameters.get(typeParameterName));
            }
        }

        // given all the possible types for every type parameter, we look at all possible
        // combinations to get the set of all fully resolved types

        Set<ResolvedType> resultingTypeSet = new HashSet<>();
        visitCombinations(
                inferredTypeParameters,
                typeParamList -> {
                    Map<String, ResolvedType> typeParamSet = new HashMap<>();
                    for (int i = 0; i < typeParamList.size(); i++) {
                        typeParamSet.put(
                                typeComponent.getTypeParameters().get(i),
                                typeParamList.get(i)
                        );
                    }

                    resultingTypeSet.add(new ResolvedType(typeComponent, typeParamSet));
                }
        );

        return resultingTypeSet;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedType that = (ResolvedType) o;
        return Objects.equals(parameterTypes, that.parameterTypes) && Objects.equals(typeComponent, that.typeComponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, typeComponent);
    }

    @Override
    public String toString() {
        if (getParametersNames().isEmpty())
            return typeComponent.getName();

        StringBuilder string = new StringBuilder();
        string.append(typeComponent.getName());

        string.append("<");
        for (String typeParameter : getParametersNames()) {
            if (getParameterTypes().containsKey(typeParameter)) {
                string.append(getParameterTypes().get(typeParameter).toString());
            } else {
                string.append(typeParameter);
            }
        }
        string.append(">");

        return string.toString();
    }

}