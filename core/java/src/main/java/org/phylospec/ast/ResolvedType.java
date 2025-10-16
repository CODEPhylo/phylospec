package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ResolvedType {
    ResolvedType(Type type, List<Set<ResolvedType>> inferredTypeParameters) {
        this.type = type;
        this.inferredTypeParameters = inferredTypeParameters;
    }

    private final List<Set<ResolvedType>> inferredTypeParameters;
    private final Type type;

    public String getName() {
        return type.getName();
    }

    public String getExtends() {
        return type.getExtends();
    }

    public List<Set<ResolvedType>> getResolvedTypeParameters() {
        return inferredTypeParameters;
    }

    public List<String> getTypeParameters() {
        return type.getTypeParameters();
    }

    public String getNamespace() {
        return type.getNamespace();
    }

    public static ResolvedType fromString(String typeString, ComponentResolver componentResolver) {
        Type typeComponent = componentResolver.resolveType(typeString);

        if (typeComponent == null) {
            throw new TypeError("Unknown type: " + typeString);
        }

        return new ResolvedType(typeComponent, new ArrayList<>());
    }


    public static ResolvedType fromString(String typeString, Map<String, Set<ResolvedType>> typeParameters, ComponentResolver componentResolver) {
        String atomicTypeString;
        String[] typeParametersNames;

        if (!typeString.contains("<")) {
            // return type is not a generic
            atomicTypeString = typeString;
            typeParametersNames = new String[]{};
        } else {
            // return type is a generic
            atomicTypeString = typeString.substring(0, typeString.indexOf("<"));

            String requiredTypeGenerics = typeString.substring(typeString.indexOf("<") + 1, typeString.length() - 1);
            typeParametersNames = requiredTypeGenerics.split(",");
        }

        Type typeComponent = componentResolver.resolveType(atomicTypeString);
        if (typeComponent == null) {
            throw new TypeError("Unknown type: " + typeString);
        }

        List<Set<ResolvedType>> inferredTypeParameters = new ArrayList<>();
        for (String typeParameterName : typeParametersNames) {
            if (typeParameters.containsKey(typeParameterName)) {
                inferredTypeParameters.add(typeParameters.get(typeParameterName));
            } else {
                inferredTypeParameters.add(Set.of(ResolvedType.fromString(typeParameterName, typeParameters, componentResolver)));
            }
        }

        return new ResolvedType(typeComponent, inferredTypeParameters);
    }
}