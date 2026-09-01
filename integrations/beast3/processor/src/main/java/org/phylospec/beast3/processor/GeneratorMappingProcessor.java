package org.phylospec.beast3.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.phylospec.annotations.GeneratorMapping;

public final class GeneratorMappingProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(GeneratorMapping.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {

        for (Element element :
                roundEnvironment.getElementsAnnotatedWith(
                        GeneratorMapping.class)) {

            if (element.getKind() != ElementKind.INTERFACE) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                "@GeneratorMapping can only be applied to an interface.",
                                element);
                continue;
            }

            TypeElement mappingDeclaration = (TypeElement) element;

            readMapping(mappingDeclaration)
                    .ifPresent(
                            mapping ->
                                    processingEnv
                                            .getMessager()
                                            .printMessage(
                                                    Diagnostic.Kind.NOTE,
                                                    "Discovered generator mapping: "
                                                            + mapping.qualifiedComponentName()
                                                            + " -> "
                                                            + mapping.implementationType(),
                                                    mapping.declaration()));
        }

        return true;
    }

    private Optional<GeneratorMappingModel> readMapping(TypeElement declaration) {
        AnnotationMirror mappingAnnotation =
                declaration.getAnnotationMirrors().stream()
                        .filter(this::isGeneratorMapping)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Expected @GeneratorMapping on "
                                                        + declaration.getQualifiedName()));

        Map<String, AnnotationValue> values = new HashMap<>();

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                processingEnv
                        .getElementUtils()
                        .getElementValuesWithDefaults(mappingAnnotation)
                        .entrySet()) {

            values.put(
                    entry.getKey().getSimpleName().toString(),
                    entry.getValue());
        }

        String qualifiedComponentName =
                (String) values.get("component").getValue();

        TypeMirror implementationType =
                (TypeMirror) values.get("implementation").getValue();

        if (qualifiedComponentName.isBlank()) {
            printError(
                    "@GeneratorMapping component must not be blank.",
                    declaration);
            return Optional.empty();
        }

        int finalSeparator = qualifiedComponentName.lastIndexOf('.');

        if (finalSeparator <= 0
                || finalSeparator == qualifiedComponentName.length() - 1) {
            printError(
                    "@GeneratorMapping component must be fully qualified, for example "
                            + "'phylospec.functions.substitution.jc69'.",
                    declaration);
            return Optional.empty();
        }

        String namespace =
                qualifiedComponentName.substring(0, finalSeparator);

        String componentName =
                qualifiedComponentName.substring(finalSeparator + 1);

        Element implementationElement =
                processingEnv
                        .getTypeUtils()
                        .asElement(implementationType);

        if (!(implementationElement
                instanceof TypeElement implementationDeclaration)
                || implementationDeclaration.getKind() != ElementKind.CLASS) {

            printError(
                    "@GeneratorMapping implementation must refer to a class.",
                    declaration);
            return Optional.empty();
        }

        if (implementationDeclaration
                .getModifiers()
                .contains(Modifier.ABSTRACT)) {

            printError(
                    "@GeneratorMapping implementation must not be abstract.",
                    declaration);
            return Optional.empty();
        }

        return Optional.of(
                new GeneratorMappingModel(
                        declaration,
                        qualifiedComponentName,
                        namespace,
                        componentName,
                        implementationType));
    }

    private boolean isGeneratorMapping(AnnotationMirror annotation) {
        Element annotationElement =
                annotation.getAnnotationType().asElement();

        return annotationElement instanceof TypeElement annotationType
                && annotationType
                .getQualifiedName()
                .contentEquals(
                        GeneratorMapping.class.getCanonicalName());
    }

    private void printError(String message, Element element) {
        processingEnv
                .getMessager()
                .printMessage(
                        Diagnostic.Kind.ERROR,
                        message,
                        element);
    }
}