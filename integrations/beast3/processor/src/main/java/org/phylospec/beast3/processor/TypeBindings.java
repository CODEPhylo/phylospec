package org.phylospec.beast3.processor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
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

    TypeBindings(ProcessingEnvironment processingEnvironment, ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
    }

    Optional<TypeMirror> resolve(String semanticType) {
        ParsedType parsedType = new ParsedType(semanticType);
        String canonicalType = resolveAlias(semanticType);

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

    private Optional<TypeMirror> resolveVector(ParsedType parsedType) {
        if (parsedType.getTypeParameters().size() != 1) {
            return Optional.empty();
        }

        String elementType =
                resolveAlias(parsedType.getTypeParameters().getFirst().getTypeString());

        return switch (elementType) {
            case TYPES + "Real" -> realVector("beast.base.spec.domain.Real");
            case TYPES + "NonNegativeReal" ->
                    realVector("beast.base.spec.domain.NonNegativeReal");
            case TYPES + "PositiveReal" ->
                    realVector("beast.base.spec.domain.PositiveReal");
            case TYPES + "Probability" ->
                    realVector("beast.base.spec.domain.UnitInterval");
            default -> Optional.empty();
        };
    }

    private String resolveAlias(String semanticType) {
        String currentType = new ParsedType(semanticType).stripGenerics();
        Set<String> visitedTypes = new HashSet<>();

        while (visitedTypes.add(currentType)) {
            Type type = componentResolver.resolveType(currentType);

            if (type == null || type.getAlias() == null || type.getAlias().isBlank()) {
                return currentType;
            }

            currentType = new ParsedType(type.getAlias()).stripGenerics();
        }

        return currentType;
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
