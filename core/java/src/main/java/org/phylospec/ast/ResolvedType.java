package org.phylospec.ast;

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

    public String getExtends() {
        return typeComponent.getExtends();
    }

    public Map<String, ResolvedType> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParametersNames() {
        return typeComponent.getTypeParameters();
    }

    /** Creates a {@link ResolvedType} object based on the type name. Note that the given type must not
     * be a generic type, and it must have been imported in the given {@link ComponentResolver}. */
    public static ResolvedType fromString(String typeString, ComponentResolver componentResolver) {
        return ResolvedType.fromString(typeString, new HashMap<>(), componentResolver).iterator().next();
    }

    /// Returns all [ResolvedType] objects that can be created based on the type name and the
    /// type parameter map. Note that the type must have been imported in the given [ComponentResolver].
    ///
    /// This method returns a set of [ResolvedType] objects because you can pass type sets for
    /// the type parameters. As an example, {@code Vector<T>} with {@code T = [Real, Integer]} will
    /// return {@code [Vector<Real>, Vector<Integer>]}.
    public static Set<ResolvedType> fromString(String typeString, Map<String, Set<ResolvedType>> typeParameters, ComponentResolver componentResolver) {
        if (!TypeUtils.isGeneric(typeString)) {
            // return type is not a generic
            Type typeComponent = componentResolver.resolveType(typeString);
            if (typeComponent == null) {
                throw new TypeError("Unknown type: " + typeString);
            }
            return Set.of(new ResolvedType(typeComponent, new HashMap<>()));
        }

        // type is a generic

        String atomicTypeString = TypeUtils.stripGenerics(typeString);
        Type typeComponent = componentResolver.resolveType(atomicTypeString);
        if (typeComponent == null) {
            throw new TypeError("Unknown type: " + typeString);
        }

        // resolve the possible type parameters

        List<String> typeParameterNames = TypeUtils.parseParameterTypeNames(typeString);
        List<Set<ResolvedType>> inferredTypeParameters = new ArrayList<>();
        for (String typeParameterName : typeParameterNames) {
            if (
                    typeComponent.getTypeParameters().contains(typeParameterName)
                            && typeParameters.containsKey(typeParameterName)
            ) {
                // parameter type is a Generic (like T) and we know its value
                inferredTypeParameters.add(typeParameters.get(typeParameterName));
            } else if (!typeComponent.getTypeParameters().contains(typeParameterName)) {
                // parameter type is another type (like Real) and we resolve it recursively
                inferredTypeParameters.add(
                        ResolvedType.fromString(typeParameterName, typeParameters, componentResolver)
                );
            }
        }

        // given all the possible types for every type parameter, we look at all possible
        // combinations to get the set of all fully resolved types

        Set<ResolvedType> resultingTypeSet = new HashSet<>();
        visitCombinations(
                typeParamList -> {
                    Map<String, ResolvedType> typeParamSet = new HashMap<>();
                    for (int i = 0; i < typeParamList.size(); i++) {
                        typeParamSet.put(
                                typeComponent.getTypeParameters().get(i),
                                typeParamList.get(i)
                        );
                    }

                    resultingTypeSet.add(new ResolvedType(typeComponent, typeParamSet));
                },
                inferredTypeParameters
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