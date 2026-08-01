package org.phylospec.typeresolver.properties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.phylospec.ast.AstVisitor;
import org.phylospec.typeresolver.ResolvedType;

public class TypePropertyUtils implements AstVisitor<Void, Void, Void> {

    public static void attachPropertyToAll(
            Set<ResolvedType> typeSet, String propertyName, Object value) {
        for (ResolvedType type : typeSet) {
            type.attachProperty(propertyName, value);
        }
    }

    public static Set<String> getCommonPropertyNames(Set<ResolvedType> typeSet) {
        if (typeSet.isEmpty()) return Set.of();

        Set<String> common = new HashSet<>();
        boolean first = true;

        for (ResolvedType type : typeSet) {
            if (first) {
                common.addAll(type.getProperties().keySet());
                first = false;
            } else {
                common.retainAll(type.getProperties().keySet());
            }
        }

        return common;
    }

    public static Object getPropertyOnAgreement(Set<ResolvedType> typeSet, String propertyName) {
        Set<Object> properties =
                typeSet.stream().map(x -> x.getProperty(propertyName)).collect(Collectors.toSet());
        if (properties.size() == 1) {
            return properties.iterator().next();
        } else {
            return null;
        }
    }

    public static void copyTypeProperties(ResolvedType targetType, ResolvedType sourceType) {
        targetType.attachProperties(sourceType.getProperties());

        List<String> targetParameterNames = targetType.getParametersNames();
        List<String> sourceParameterNames = sourceType.getParametersNames();
        int parameterCount = Math.min(targetParameterNames.size(), sourceParameterNames.size());

        for (int index = 0; index < parameterCount; index++) {
            ResolvedType targetParameter =
                    targetType.getParameterTypes().get(targetParameterNames.get(index));
            ResolvedType sourceParameter =
                    sourceType.getParameterTypes().get(sourceParameterNames.get(index));

            if (targetParameter != null && sourceParameter != null) {
                copyTypeProperties(targetParameter, sourceParameter);
            }
        }
    }

    public static boolean disagreeIfKnown(Object a, Object b) {
        if (a == null || b == null) return false;
        if (a instanceof Number aNumber && b instanceof Number bNumber) {
            return Double.compare(aNumber.doubleValue(), bNumber.doubleValue()) != 0;
        }
        return !a.equals(b);
    }
}
