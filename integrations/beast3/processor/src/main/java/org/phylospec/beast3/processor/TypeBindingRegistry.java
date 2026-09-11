package org.phylospec.beast3.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.phylospec.annotations.TypeBinding;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

/** Collects and validates package-defined semantic type bindings. */
final class TypeBindingRegistry {

    private final ProcessingEnvironment processingEnvironment;
    private final ComponentResolver componentResolver;
    private final Elements elements;
    private final Types types;
    private final Map<String, TypeMirror> bindings = new LinkedHashMap<>();

    TypeBindingRegistry(
            ProcessingEnvironment processingEnvironment, ComponentResolver componentResolver) {
        this.processingEnvironment = processingEnvironment;
        this.componentResolver = componentResolver;
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
    }

    Optional<TypeMirror> resolve(String semanticType) {
        return Optional.ofNullable(bindings.get(semanticType));
    }

    void register(
            RoundEnvironment roundEnvironment,
            Function<String, Optional<TypeMirror>> typeResolver,
            Predicate<String> hasBuiltInBinding) {
        Map<String, TypeElement> newDeclarations = new LinkedHashMap<>();

        for (Element element : roundEnvironment.getElementsAnnotatedWith(TypeBinding.class)) {
            if (element.getKind() != ElementKind.CLASS
                    && element.getKind() != ElementKind.INTERFACE) {
                printError(
                        "@TypeBinding can only be applied to a class or mapping interface.",
                        element);
                continue;
            }

            TypeElement declaration = (TypeElement) element;
            TypeBinding annotation = declaration.getAnnotation(TypeBinding.class);
            String semanticType = annotation.semantic().trim();

            if (!isQualifiedAtomicType(semanticType)) {
                printError(
                        "@TypeBinding semantic must be a fully-qualified, non-generic "
                                + "PhyloSpec type, but was '"
                                + semanticType
                                + "'.",
                        declaration);
                continue;
            }

            Type componentType = componentResolver.resolveType(semanticType);

            if (componentType == null) {
                printError(
                        "@TypeBinding refers to unknown PhyloSpec type '"
                                + semanticType
                                + "'. Load the component library that declares this type with '-A"
                                + TileProcessor.COMPONENT_LIBRARIES_OPTION
                                + "=<path>'.",
                        declaration);
                continue;
            }

            if (!componentType.getTypeParameters().isEmpty()) {
                printError(
                        "@TypeBinding does not yet support generic PhyloSpec type '"
                                + semanticType
                                + "'.",
                        declaration);
                continue;
            }

            if (hasBuiltInBinding.test(semanticType)) {
                printError(
                        "PhyloSpec type '"
                                + semanticType
                                + "' already has a built-in BEAST Java binding.",
                        declaration);
                continue;
            }

            Optional<TypeMirror> implementation = implementationType(declaration, annotation);

            if (implementation.isEmpty()) {
                continue;
            }

            TypeMirror previous = bindings.putIfAbsent(semanticType, implementation.orElseThrow());

            if (previous != null) {
                printError(
                        "Duplicate @TypeBinding for PhyloSpec type '" + semanticType + "'.",
                        declaration);
            } else {
                newDeclarations.put(semanticType, declaration);
            }
        }

        validateSemanticParents(newDeclarations, typeResolver);
    }

    private void validateSemanticParents(
            Map<String, TypeElement> declarations,
            Function<String, Optional<TypeMirror>> typeResolver) {
        for (Map.Entry<String, TypeElement> entry : declarations.entrySet()) {
            Type semanticType = componentResolver.resolveType(entry.getKey());

            if (semanticType.getExtends() == null || semanticType.getExtends().isBlank()) {
                continue;
            }

            Optional<TypeMirror> parentBinding = typeResolver.apply(semanticType.getExtends());

            if (parentBinding.isEmpty()) {
                printError(
                        "Cannot validate @TypeBinding for '"
                                + entry.getKey()
                                + "' because its parent semantic type '"
                                + semanticType.getExtends()
                                + "' has no Java binding.",
                        entry.getValue());
                continue;
            }

            TypeMirror implementation = bindings.get(entry.getKey());
            if (!types.isAssignable(implementation, parentBinding.orElseThrow())) {
                printError(
                        "@TypeBinding implementation '"
                                + implementation
                                + "' is not assignable to Java type '"
                                + parentBinding.orElseThrow()
                                + "' required by parent semantic type '"
                                + semanticType.getExtends()
                                + "'.",
                        entry.getValue());
            }
        }
    }

    private Optional<TypeMirror> implementationType(
            TypeElement declaration, TypeBinding annotation) {
        TypeMirror configuredType;

        try {
            annotation.implementation();
            throw new IllegalStateException("Expected a mirrored annotation type.");
        } catch (MirroredTypeException exception) {
            configuredType = exception.getTypeMirror();
        }

        TypeElement voidType = elements.getTypeElement(Void.class.getCanonicalName());
        boolean usesDefault =
                voidType != null
                        && types.isSameType(
                                types.erasure(configuredType), types.erasure(voidType.asType()));

        TypeMirror implementation;
        if (usesDefault) {
            if (declaration.getKind() != ElementKind.CLASS) {
                printError(
                        "@TypeBinding on a mapping interface must specify implementation.",
                        declaration);
                return Optional.empty();
            }
            implementation = declaration.asType();
        } else {
            implementation = configuredType;
        }

        if (implementation.getKind() != TypeKind.DECLARED) {
            printError("@TypeBinding implementation must be a declared Java type.", declaration);
            return Optional.empty();
        }

        TypeElement implementationDeclaration =
                (TypeElement) ((DeclaredType) implementation).asElement();

        if (!implementationDeclaration.getModifiers().contains(Modifier.PUBLIC)) {
            printError("@TypeBinding implementation must be public.", declaration);
            return Optional.empty();
        }

        return Optional.of(implementation);
    }

    private boolean isQualifiedAtomicType(String semanticType) {
        return !semanticType.isBlank()
                && !semanticType.contains("<")
                && SourceVersion.isName(semanticType);
    }

    private void printError(String message, Element element) {
        processingEnvironment
                .getMessager()
                .printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
