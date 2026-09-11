package org.phylospec.beast3.processor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.ParsedType;
import org.phylospec.components.Type;

/** Resolves engine-independent PhyloSpec types to their BEAST 3 Java representations. */
final class TypeBindings {

    private static final String TYPES = "phylospec.types.";

    private final ComponentResolver componentResolver;
    private final Elements elements;
    private final Types types;
    private final TypeBindingRegistry packageBindings;

    TypeBindings(ProcessingEnvironment processingEnvironment, ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
        this.packageBindings =
                new TypeBindingRegistry(processingEnvironment, componentResolver);
    }

    Optional<TypeMirror> resolve(String semanticType) {
        ParsedType requestedType = new ParsedType(semanticType);
        Optional<TypeMirror> requestedBinding =
                packageBindings.resolve(requestedType.stripGenerics());

        if (requestedBinding.isPresent() && requestedType.getTypeParameters().isEmpty()) {
            return requestedBinding;
        }

        Optional<String> resolvedType = resolveAlias(semanticType);

        if (resolvedType.isEmpty()) {
            return Optional.empty();
        }

        ParsedType parsedType = new ParsedType(resolvedType.orElseThrow());
        String canonicalType = parsedType.stripGenerics();
        Optional<TypeMirror> packageBinding = packageBindings.resolve(canonicalType);

        if (packageBinding.isPresent() && parsedType.getTypeParameters().isEmpty()) {
            return packageBinding;
        }

        return switch (canonicalType) {
            case TYPES + "Boolean" -> declaredType("beast.base.spec.type.BoolScalar");
            case TYPES + "String" -> declaredType("java.lang.String");
            case TYPES + "Real" -> realScalar("beast.base.spec.domain.Real");
            case TYPES + "NonNegativeReal" -> realScalar("beast.base.spec.domain.NonNegativeReal");
            case TYPES + "PositiveReal" -> realScalar("beast.base.spec.domain.PositiveReal");
            case TYPES + "Probability" -> realScalar("beast.base.spec.domain.UnitInterval");
            case TYPES + "Integer" -> intScalar("beast.base.spec.domain.Int");
            case TYPES + "NonNegativeInteger" -> intScalar("beast.base.spec.domain.NonNegativeInt");
            case TYPES + "PositiveInteger" -> intScalar("beast.base.spec.domain.PositiveInt");
            case TYPES + "Simplex" -> declaredType("beast.base.spec.type.Simplex");
            case TYPES + "Vector" -> resolveVector(parsedType);
            case TYPES + "Tree" -> declaredType("beast.base.evolution.tree.Tree");
            case TYPES + "Alignment" -> declaredType("tiles.input.DecoratedAlignment");
            case TYPES + "QMatrix" ->
                    declaredType("beast.base.evolution.substitutionmodel.SubstitutionModel");
            case TYPES + "PopulationFunction" ->
                    declaredType("beast.base.evolution.tree.coalescent.PopulationFunction");
            default -> Optional.empty();
        };
    }

    void register(RoundEnvironment roundEnvironment) {
        packageBindings.register(roundEnvironment, this::resolve, this::isBuiltIn);
    }

    private boolean isBuiltIn(String semanticType) {
        Optional<String> resolvedType = resolveAlias(semanticType);

        if (resolvedType.isEmpty()) {
            return false;
        }

        String canonicalType = new ParsedType(resolvedType.orElseThrow()).stripGenerics();

        return switch (canonicalType) {
            case TYPES + "Boolean",
                    TYPES + "String",
                    TYPES + "Real",
                    TYPES + "NonNegativeReal",
                    TYPES + "PositiveReal",
                    TYPES + "Probability",
                    TYPES + "Integer",
                    TYPES + "NonNegativeInteger",
                    TYPES + "PositiveInteger",
                    TYPES + "Simplex",
                    TYPES + "Vector",
                    TYPES + "Tree",
                    TYPES + "Alignment",
                    TYPES + "QMatrix",
                    TYPES + "PopulationFunction" -> true;
            default -> false;
        };
    }

    private Optional<TypeMirror> resolveVector(ParsedType parsedType) {
        if (parsedType.getTypeParameters().size() != 1) {
            return Optional.empty();
        }

        String semanticElementType =
                parsedType.getTypeParameters().getFirst().getTypeString();
        Optional<String> resolvedElementType = resolveAlias(semanticElementType);

        if (resolvedElementType.isEmpty()) {
            return Optional.empty();
        }

        String elementType =
                new ParsedType(resolvedElementType.orElseThrow()).stripGenerics();

        return switch (elementType) {
            case TYPES + "Real" -> realVector("beast.base.spec.domain.Real");
            case TYPES + "NonNegativeReal" ->
                    realVector("beast.base.spec.domain.NonNegativeReal");
            case TYPES + "PositiveReal" ->
                    realVector("beast.base.spec.domain.PositiveReal");
            case TYPES + "Probability" ->
                    realVector("beast.base.spec.domain.UnitInterval");
            default -> objectVector(semanticElementType);
        };
    }

    private Optional<TypeMirror> objectVector(String semanticElementType) {
        TypeElement list = elements.getTypeElement("java.util.List");
        Optional<TypeMirror> elementType = resolve(semanticElementType);

        if (list == null || elementType.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(types.getDeclaredType(list, elementType.orElseThrow()));
    }

    private Optional<String> resolveAlias(String semanticType) {
        return resolveAlias(semanticType, componentResolver);
    }

    static Optional<String> resolveAlias(
            String semanticType, ComponentResolver componentResolver) {
        String currentType = semanticType;
        Set<String> visitedTypes = new HashSet<>();

        while (true) {
            ParsedType parsedType = new ParsedType(currentType);
            String baseType = parsedType.stripGenerics();

            if (!visitedTypes.add(baseType)) {
                return Optional.empty();
            }

            Type type = componentResolver.resolveType(baseType);

            if (type == null || type.getAlias() == null || type.getAlias().isBlank()) {
                return Optional.of(currentType);
            }

            if (type.getTypeParameters().size()
                    != parsedType.getTypeParameters().size()) {
                return Optional.empty();
            }

            Map<String, String> substitutions = new LinkedHashMap<>();
            for (int index = 0; index < type.getTypeParameters().size(); index++) {
                substitutions.put(
                        type.getTypeParameters().get(index),
                        parsedType.getTypeParameters().get(index).getTypeString());
            }

            currentType = substitute(type.getAlias(), substitutions);
        }
    }

    private static String substitute(
            String semanticType, Map<String, String> substitutions) {
        ParsedType parsedType = new ParsedType(semanticType);

        if (parsedType.getNamespace().isEmpty()
                && parsedType.getTypeParameters().isEmpty()
                && substitutions.containsKey(parsedType.getAtomicTypeName())) {
            return substitutions.get(parsedType.getAtomicTypeName());
        }

        if (parsedType.getTypeParameters().isEmpty()
                && parsedType.getTypeProperties().isEmpty()) {
            return parsedType.stripGenerics();
        }

        String parameters = parsedType.getTypeParameters().stream()
                .map(parameter -> substitute(parameter.getTypeString(), substitutions))
                .collect(Collectors.joining(","));
        String properties = parsedType.getTypeProperties().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        StringBuilder resolvedType =
                new StringBuilder(parsedType.stripGenerics()).append("<").append(parameters);

        if (!properties.isEmpty()) {
            resolvedType.append(";").append(properties);
        }

        return resolvedType.append(">").toString();
    }

    private Optional<TypeMirror> realScalar(String domainClass) {
        return parameterizedType("beast.base.spec.type.RealScalar", domainClass);
    }

    private Optional<TypeMirror> intScalar(String domainClass) {
        return parameterizedType("beast.base.spec.type.IntScalar", domainClass);
    }

    private Optional<TypeMirror> realVector(String domainClass) {
        return parameterizedType("beast.base.spec.type.RealVector", domainClass);
    }

    private Optional<TypeMirror> parameterizedType(String wrapperClass, String domainClass) {
        TypeElement wrapper = elements.getTypeElement(wrapperClass);
        TypeElement domain = elements.getTypeElement(domainClass);

        if (wrapper == null || domain == null) {
            return Optional.empty();
        }

        TypeMirror domainWildcard = types.getWildcardType(domain.asType(), null);
        return Optional.of(types.getDeclaredType(wrapper, domainWildcard));
    }

    private Optional<TypeMirror> declaredType(String className) {
        TypeElement declaration = elements.getTypeElement(className);
        return declaration == null ? Optional.empty() : Optional.of(declaration.asType());
    }
}
