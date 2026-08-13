package org.phylospec.typeresolver;

import static org.phylospec.Utils.visitCombinations;

import java.util.*;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.ParsedType;
import org.phylospec.components.Type;
import org.phylospec.typeresolver.properties.TypeProperties;

/// This class represents a fully resolved type.
///
/// Each resolved type has a reference to the corresponding type component
/// and a map binding the generic type parameters to other resolved types.
public class ResolvedType {
    ResolvedType(Type typeComponent, Map<String, ResolvedType> parameterTypes) {
        this(typeComponent, parameterTypes, new TypeProperties());
    }

    private ResolvedType(Type typeComponent, Map<String, ResolvedType> parameterTypes, TypeProperties typeProperties) {
        this.typeComponent = typeComponent;
        this.parameterTypes = parameterTypes;
        this.typeProperties = typeProperties;
    }

    private final Map<String, ResolvedType> parameterTypes;
    private final TypeProperties typeProperties;
    private final Type typeComponent;

    public Type getTypeComponent() {
        return typeComponent;
    }

    public String getName() {
        return typeComponent.getName();
    }

    public String getUnqualifiedName() {
        String[] splitNamespace = typeComponent.getName().split("\\.");
        return splitNamespace[splitNamespace.length - 1];
    }

    public boolean hasUnresolvedParameterTypes() {
        return getParameterTypes().size() != typeComponent.getTypeParameters().size();
    }

    public String getExtends() {
        return typeComponent.getExtends();
    }

    public String getAlias() {
        return typeComponent.getAlias();
    }

    public Map<String, ResolvedType> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParametersNames() {
        return typeComponent.getTypeParameters();
    }

    public TypeProperties properties() {
        return this.typeProperties;
    }

    /**
     * Creates a deep copy of this resolved type. The type component is shared with the
     * original, but the parameter types and the type properties are copied into
     * independent instances.
     */
    public ResolvedType deepCopy() {
        Map<String, ResolvedType> copiedParameterTypes = new HashMap<>();
        for (String parameterName : parameterTypes.keySet()) {
            copiedParameterTypes.put(
                    parameterName, parameterTypes.get(parameterName).deepCopy());
        }
        return new ResolvedType(typeComponent, copiedParameterTypes, typeProperties.copy());
    }

    /**
     * Creates a {@link ResolvedType} object based on the type name. Note that the given type must not
     * be a generic type, and it must have been imported in the given {@link ComponentResolver}.
     * This method throws an error if the type parameters cannot be fully resolved from the given string.
     */
    public static ResolvedTypeSet fromString(String typeString, ComponentResolver componentResolver) {
        return ResolvedType.fromString(typeString, componentResolver, false);
    }

    /**
     * Creates a {@link ResolvedType} object based on the type name. Note that the given type must not
     * be a generic type, and it must have been imported in the given {@link ComponentResolver}.
     */
    public static ResolvedTypeSet fromString(
            String typeString, ComponentResolver componentResolver, boolean allowUnresolvedTypeParameter) {
        return ResolvedType.fromString(typeString, new HashMap<>(), componentResolver, allowUnresolvedTypeParameter);
    }

    /**
     * Creates a {@link ResolvedType} object based on the type name and the given resolved type parameters in
     * correct order.
     * Note that the given type must have been imported in the given {@link ComponentResolver}.
     */
    public static ResolvedTypeSet fromString(
            String typeString,
            List<ResolvedTypeSet> typeParameters,
            ComponentResolver componentResolver,
            boolean allowUnresolvedTypeParameter) {
        // we build a map of the type parameters and then use the more general overloaded method

        Type typeComponent = componentResolver.resolveType(typeString);
        if (typeComponent == null) {
            throw new TypeError(
                    "The type '" + typeString + "' does not exist.",
                    "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?");
        }

        if (typeParameters.size() != typeComponent.getTypeParameters().size()) {
            throw new TypeError(
                    "The type '"
                            + typeString
                            + "' takes "
                            + typeComponent.getTypeParameters().size()
                            + " type parameters, but you provided "
                            + typeParameters.size()
                            + ".",
                    "Provide exactly " + typeComponent.getTypeParameters().size() + " type parameters.");
        }

        Map<String, ResolvedTypeSet> typeParameterMap = new HashMap<>();
        for (int i = 0; i < typeParameters.size(); i++) {
            typeParameterMap.put(typeComponent.getTypeParameters().get(i), typeParameters.get(i));
        }

        return ResolvedType.fromString(typeString, typeParameterMap, componentResolver, allowUnresolvedTypeParameter);
    }

    /// Returns all [ResolvedType] objects that can be created based on the type name and the
    /// type parameter map. Note that the type must have been imported in the given
    // [ComponentResolver].
    ///
    /// This method returns a set of [ResolvedType] objects because you can pass type sets for
    /// the type parameters. As an example, {@code Vector<T>} with {@code T = [Real, Integer]} will
    /// return {@code [Vector<Real>, Vector<Integer>]}.
    ///
    /// This method throws an error if the type parameters cannot be fully resolved from the given
    // string.
    public static ResolvedTypeSet fromString(
            String typeString, Map<String, ResolvedTypeSet> typeParameters, ComponentResolver componentResolver) {
        return ResolvedType.fromString(typeString, typeParameters, componentResolver, false);
    }

    /// Returns all [ResolvedType] objects that can be created based on the type name and the
    /// type parameter map. Note that the type must have been imported in the given
    // [ComponentResolver].
    ///
    /// This method returns a set of [ResolvedType] objects because you can pass type sets for
    /// the type parameters. As an example, {@code Vector<T>} with {@code T = [Real, Integer]} will
    /// return {@code [Vector<Real>, Vector<Integer>]}.
    public static ResolvedTypeSet fromString(
            String typeString,
            Map<String, ResolvedTypeSet> typeParameters,
            ComponentResolver componentResolver,
            boolean allowUnresolvedTypeParameter) {
        Type typeComponent = componentResolver.resolveType(typeString);
        if (typeComponent == null) {
            throw new TypeError(
                    "The type '" + typeString + "' does not exist.",
                    "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?");
        }

        // resolve the possible type parameters

        List<ParsedType> typeParameterNames = new ParsedType(typeString).getTypeParameters();
        List<ResolvedTypeSet> inferredTypeParameters = new ArrayList<>();

        if (new ParsedType(typeString).isGeneric()) {
            // in this case, the given type string directly indicates the type parameters (e.g.
            // Vector<Real>).
            // we resolve the type parameters from the string

            for (ParsedType typeParameter : typeParameterNames) {
                String typeParameterName = typeParameter.getTypeString();
                if (typeComponent.getTypeParameters().contains(typeParameterName)
                        && typeParameters.containsKey(typeParameterName)) {
                    // parameter type is a Generic (like T) and we know its value
                    inferredTypeParameters.add(typeParameters.get(typeParameterName));
                } else {
                    // parameter type is another type (like Real) and we resolve it recursively
                    inferredTypeParameters.add(ResolvedType.fromString(
                            typeParameterName, typeParameters, componentResolver, allowUnresolvedTypeParameter));
                }
            }
        } else {
            // in this case, the type string has no indication of type parameters
            // however, the type might still be a generic one, and we got the resolved parameter
            // types through the
            // passed 'typeParameters'

            for (String typeParameterName : typeComponent.getTypeParameters()) {
                if (typeParameters.containsKey(typeParameterName)) {
                    inferredTypeParameters.add(typeParameters.get(typeParameterName));
                } else if (!allowUnresolvedTypeParameter) {
                    throw new TypeError(
                            "The type '" + typeString + "' does not exist.",
                            "Are you looking for '" + componentResolver.findClosestType(typeString) + "'?");
                }
            }
        }

        // given all the possible types for every type parameter, we look at all possible
        // combinations to get the set of all fully resolved types

        ResolvedTypeSet resultingTypeSet = new ResolvedTypeSet();
        visitCombinations(inferredTypeParameters, typeParamList -> {
            Map<String, ResolvedType> typeParamSet = new HashMap<>();
            for (int i = 0; i < typeParamList.size(); i++) {
                typeParamSet.put(
                        typeComponent.getTypeParameters().get(i),
                        typeParamList.get(i).deepCopy());
            }

            resultingTypeSet.add(new ResolvedType(typeComponent, typeParamSet));
        });

        if (typeComponent.getAlias() != null) {
            // we have an alias
            // we resolve the aliased type for every concrete parameter combination and return the set of both
            ResolvedTypeSet aliasedTypeSet = new ResolvedTypeSet();
            for (ResolvedType resultingType : resultingTypeSet) {
                Map<String, ResolvedTypeSet> resolvedAliasParameters = new HashMap<>();
                for (Map.Entry<String, ResolvedType> parameterType :
                        resultingType.getParameterTypes().entrySet()) {
                    resolvedAliasParameters.put(parameterType.getKey(), ResolvedTypeSet.of(parameterType.getValue()));
                }
                aliasedTypeSet.addAll(ResolvedType.fromString(
                        typeComponent.getAlias(),
                        resolvedAliasParameters,
                        componentResolver,
                        allowUnresolvedTypeParameter));
            }
            resultingTypeSet.addAll(aliasedTypeSet);
        }

        return resultingTypeSet;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedType that = (ResolvedType) o;
        return Objects.equals(parameterTypes, that.parameterTypes) && Objects.equals(typeComponent, that.typeComponent);
    }

    public boolean equalsIncludingProperties(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedType that = (ResolvedType) o;
        return Objects.equals(parameterTypes, that.parameterTypes)
                && Objects.equals(typeComponent, that.typeComponent)
                && Objects.equals(properties(), that.properties());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, typeComponent);
    }

    public int hashCodeIncludingProperties() {
        return Objects.hash(parameterTypes, typeComponent, properties());
    }

    @Override
    public String toString() {
        if (getParametersNames().isEmpty()) return getUnqualifiedName();

        StringBuilder string = new StringBuilder();
        string.append(getUnqualifiedName());

        string.append("<");
        List<String> paramStrings = new ArrayList<>();
        for (String typeParameter : getParametersNames()) {
            if (getParameterTypes().containsKey(typeParameter)) {
                paramStrings.add(getParameterTypes().get(typeParameter).toString());
            } else {
                paramStrings.add(typeParameter);
            }
        }
        string.append(String.join(", ", paramStrings));
        string.append(">");

        return string.toString();
    }
}
