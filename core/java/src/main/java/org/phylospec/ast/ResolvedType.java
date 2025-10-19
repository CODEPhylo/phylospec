package org.phylospec.ast;

import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

import java.util.*;

import static org.phylospec.Utils.visitCombinations;

public class ResolvedType {
    ResolvedType(Type type, Map<String, ResolvedType> parameterTypes) {
        this.type = type;
        this.parameterTypes = parameterTypes;
    }

    private final Map<String, ResolvedType> parameterTypes;
    private final Type type;

    public Type getTypeComponent() {
        return type;
    }

    public String getName() {
        return type.getName();
    }

    public String getExtends() {
        return type.getExtends();
    }

    public Map<String, ResolvedType> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParametersNames() {
        return type.getTypeParameters();
    }

    public static ResolvedType fromString(String typeString, ComponentResolver componentResolver) {
        return ResolvedType.fromString(typeString, new HashMap<>(), componentResolver).iterator().next();
    }

    public static Set<ResolvedType> fromString(String typeString, Map<String, Set<ResolvedType>> typeParameters, ComponentResolver componentResolver) {
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
            if (typeComponent.getTypeParameters().contains(typeParameterName) && typeParameters.containsKey(typeParameterName)) {
                inferredTypeParameters.add(typeParameters.get(typeParameterName));
            } else if (!typeComponent.getTypeParameters().contains(typeParameterName)) {
                inferredTypeParameters.add(ResolvedType.fromString(typeParameterName, typeParameters, componentResolver));
            }
        }

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
        return Objects.equals(parameterTypes, that.parameterTypes) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, type);
    }
}