package org.phylospec.typeresolver.properties;

import static org.phylospec.typeresolver.properties.TypePropertyNames.*;

import java.util.*;
import java.util.stream.Collectors;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.ResolvedTypeSet;
import org.phylospec.typeresolver.TypeUtils;
import org.phylospec.workspace.Workspace;

/**
 * Coordinates the inference, propagation, and validation of properties on resolved types.
 * Generator-specific property processing is delegated to {@link GeneratorPropertyResolver}.
 */
public class TypePropertyEngine {

    private final List<ErrorEventListener> eventListeners;
    private final GeneratorPropertyResolver generatorPropertyResolver;

    public TypePropertyEngine(Workspace workspace) {
        eventListeners = new ArrayList<>();
        generatorPropertyResolver = new GeneratorPropertyResolver(workspace);
    }

    public void registerEventListener(ErrorEventListener listener) {
        eventListeners.add(listener);
        generatorPropertyResolver.registerEventListener(listener);
    }

    private void raiseWarning(Error warning) {
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.warningDetected(warning);
        }
    }

    public void resolveAssignment(ResolvedTypeSet resolvedVariableTypeSet, ResolvedTypeSet resolvedExpressionTypeSet) {
        for (ResolvedType resolvedVariableType : resolvedVariableTypeSet) {
            for (ResolvedType resolvedExpressionType : resolvedExpressionTypeSet) {
                copyProperties(resolvedExpressionType, resolvedVariableType);
            }
        }
    }

    public void resolveDraw(
            ResolvedTypeSet resolvedVariableTypeSet,
            ResolvedTypeSet resolvedExpressionTypeSet,
            ComponentResolver componentResolver) {
        for (ResolvedType resolvedVariableType : resolvedVariableTypeSet) {
            // find the matching resolved types
            for (ResolvedType resolvedExpressionType : resolvedExpressionTypeSet) {
                ResolvedType unwrappedExpressionType = TypeUtils.recoverTypeParameter(
                        "phylospec.types.Distribution", "T", resolvedExpressionType, componentResolver);
                if (unwrappedExpressionType != null) {
                    copyProperties(unwrappedExpressionType, resolvedVariableType);
                }
            }
        }
    }

    public void resolveIndexed(int indexCount, List<ResolvedTypeSet> rangeTypeSets, ResolvedTypeSet containerTypeSet) {
        if (indexCount == 1) {
            Object num = getPropertyOnAgreement(rangeTypeSets.getFirst(), NUM);
            attachToAll(containerTypeSet, NUM, num);
        } else {
            Object numRows = getPropertyOnAgreement(rangeTypeSets.get(0), NUM);
            Object numCols = getPropertyOnAgreement(rangeTypeSets.get(1), NUM);
            attachToAll(containerTypeSet, NUM, numRows);
            attachToAll(containerTypeSet, NUM_ROWS, numRows);
            attachToAll(containerTypeSet, NUM_COLS, numCols);
        }
    }

    public void checkObservedAs(
            Stmt.ObservedAs observedAs, ResolvedTypeSet generatedTypeSet, ResolvedTypeSet observationTypeSet) {
        Set<String> commonProperties = getCommonProperties(generatedTypeSet);
        for (String propertyName : commonProperties) {
            Object generatedProperty = getPropertyOnAgreement(generatedTypeSet, propertyName);
            Object observedProperty = getPropertyOnAgreement(observationTypeSet, propertyName);

            if (disagreeIfKnown(generatedProperty, observedProperty)) {
                raiseWarning(new Error(
                        observedAs.getRange(),
                        "The observation might not be compatible with the generated value.",
                        propertyName
                                + " is "
                                + observedProperty
                                + " for the observation, but "
                                + generatedProperty
                                + " for '"
                                + observedAs.getName()
                                + "'."));
            }
        }
    }

    private boolean disagreeIfKnown(Object a, Object b) {
        if (a == null || b == null) return false;
        if (a instanceof Number aNumber && b instanceof Number bNumber) {
            return Double.compare(aNumber.doubleValue(), bNumber.doubleValue()) != 0;
        }
        return !a.equals(b);
    }

    public void resolveLiteral(ResolvedTypeSet resolvedTypeSet, Object value) {
        for (ResolvedType resolvedType : resolvedTypeSet) {
            resolvedType.properties().attach(VALUE, value);
        }
    }

    public void processGenerator(
            Expr.Call call, Generator generator, TypeUtils.ResolvedGeneratorApplication resolvedGeneratorApplication) {
        generatorPropertyResolver.processGenerator(call, generator, resolvedGeneratorApplication);
    }

    public void resolveArray(int numElements, ResolvedTypeSet arrayTypeSet) {
        for (ResolvedType resolvedType : arrayTypeSet) {
            resolvedType.properties().attach(NUM, numElements);
        }
    }

    public void checkIndex(
            List<Expr> indices, List<ResolvedTypeSet> resolvedIndexTypeSets, ResolvedTypeSet containerTypeSet) {
        // check that the index is in range in case we have a num property

        // check the first dimension using the num property

        Object firstIndexLiteral = getPropertyOnAgreement(resolvedIndexTypeSets.getFirst(), VALUE);
        Object containerSize = getPropertyOnAgreement(containerTypeSet, NUM);

        if (firstIndexLiteral instanceof Number indexNr && containerSize instanceof Number sizeNr) {
            long indexValue = indexNr.longValue();
            long sizeValue = sizeNr.longValue();
            if (indexValue < 1 || sizeValue < indexValue) {
                raiseWarning(new Error(
                        indices.getFirst().getRange(),
                        "The index might be out of range.",
                        "Use an index between 1 and " + sizeValue + "."));
            }
        }

        // check a possible second dimension using the numCols property

        if (indices.size() == 2) {
            Object secondIndexLiteral = getPropertyOnAgreement(resolvedIndexTypeSets.get(1), VALUE);
            Object numCols = getPropertyOnAgreement(containerTypeSet, NUM_COLS);

            if (secondIndexLiteral instanceof Number indexNr && numCols instanceof Number sizeNr) {
                long indexValue = indexNr.longValue();
                long sizeValue = sizeNr.longValue();
                if (indexValue < 1 || sizeValue < indexValue) {
                    raiseWarning(new Error(
                            indices.get(1).getRange(),
                            "The index might be out of range.",
                            "Use an index between 1 and " + sizeValue + "."));
                }
            }
        }
    }

    public void resolveRange(
            ResolvedTypeSet fromTypeSet, ResolvedTypeSet toTypeSet, ResolvedTypeSet listComprehensionTypeSet) {
        // check if type range has literal properties and determine the result num property if
        // possible

        Object fromLiteral = getPropertyOnAgreement(fromTypeSet, VALUE);
        Object toLiteral = getPropertyOnAgreement(toTypeSet, VALUE);
        if (fromLiteral instanceof Number fromNumber && toLiteral instanceof Number toNumber) {
            long fromValue = fromNumber.longValue();
            long toValue = toNumber.longValue();
            long rangeSize = Math.abs(toValue - fromValue) + 1;
            attachToAll(listComprehensionTypeSet, NUM, rangeSize);
        }
    }

    private static Set<String> getCommonProperties(ResolvedTypeSet typeSet) {
        if (typeSet.isEmpty()) return Set.of();

        Set<String> common = new HashSet<>();
        boolean first = true;

        for (ResolvedType type : typeSet) {
            if (first) {
                common.addAll(type.properties().getPropertyNames());
                first = false;
            } else {
                common.retainAll(type.properties().getPropertyNames());
            }
        }

        return common;
    }

    public static Object getPropertyOnAgreement(ResolvedTypeSet typeSet, String propertyName) {
        Set<Object> properties =
                typeSet.stream().map(x -> x.properties().get(propertyName)).collect(Collectors.toSet());
        if (properties.size() == 1) {
            return properties.iterator().next();
        } else {
            return null;
        }
    }

    private void attachToAll(ResolvedTypeSet typeSet, String propertyName, Object value) {
        for (ResolvedType type : typeSet) {
            type.properties().attach(propertyName, value);
        }
    }

    public void copyProperties(ResolvedType sourceType, ResolvedType targetType) {
        targetType.properties().attach(sourceType.properties());

        // recursively copy the properties of the type parameters

        List<String> targetParameterNames = targetType.getParametersNames();
        List<String> sourceParameterNames = sourceType.getParametersNames();
        int parameterCount = Math.min(targetParameterNames.size(), sourceParameterNames.size());

        for (int index = 0; index < parameterCount; index++) {
            ResolvedType targetParameter = targetType.getParameterTypes().get(targetParameterNames.get(index));
            ResolvedType sourceParameter = sourceType.getParameterTypes().get(sourceParameterNames.get(index));

            if (targetParameter != null && sourceParameter != null) {
                copyProperties(sourceParameter, targetParameter);
            }
        }
    }

    /**
     * Returns the properties that every type in {@code typeList} agrees on. This is the
     * reconciliation primitive {@link ResolvedTypeSet} uses when it merges two structurally-equal
     * types on insertion.
     *
     * The types are collected into an identity-based set on purpose, not a {@link ResolvedTypeSet}
     * or plain {@code HashSet}: the whole point of this method is to compare the properties of
     * types that may be structurally equal (per {@link ResolvedType#equals}) but carry different
     * properties. Deduping by structural equality here would collapse those instances into one
     * before their properties could ever be compared, always reporting trivial "agreement".
     */
    public static Map<String, Object> getPropertiesInAgreement(ResolvedType type1, ResolvedType type2) {
        Map<String, Object> commonProperties = new HashMap<>();

        for (String name : type1.properties().getPropertyNames()) {
            if (!type2.properties().has(name)) continue;

            Object type1Property = type1.properties().get(name);
            Object type2Property = type2.properties().get(name);

            if (type1Property.equals(type2Property)) commonProperties.put(name, type1Property);
        }

        return commonProperties;
    }
}
