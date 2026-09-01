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
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import org.phylospec.annotations.GeneratorMapping;

public final class GeneratorMappingProcessor extends AbstractProcessor {

    private GeneratorTileWriter tileWriter;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(GeneratorMapping.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(
            ProcessingEnvironment processingEnvironment) {

        super.init(processingEnvironment);

        this.tileWriter =
                new GeneratorTileWriter(
                        processingEnvironment.getFiler());
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
                    .ifPresent(this::generateTile);
        }

        return true;
    }

    private Optional<GeneratorMappingModel> readMapping(
            TypeElement declaration) {

        if (!ElementFilter.methodsIn(
                        declaration.getEnclosedElements())
                .isEmpty()) {

            printError(
                    "Automatic Tile generation currently supports only "
                            + "zero-input mappings. Input mappings are not supported yet.",
                    declaration);

            return Optional.empty();
        }

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

        if (!implementationDeclaration
                .getModifiers()
                .contains(Modifier.PUBLIC)) {

            printError(
                    "@GeneratorMapping implementation must be public.",
                    declaration);

            return Optional.empty();
        }

        boolean hasPublicNoArgumentConstructor =
                ElementFilter.constructorsIn(
                                implementationDeclaration.getEnclosedElements())
                        .stream()
                        .anyMatch(
                                constructor ->
                                        constructor.getParameters().isEmpty()
                                                && constructor
                                                .getModifiers()
                                                .contains(Modifier.PUBLIC));

        if (!hasPublicNoArgumentConstructor) {
            printError(
                    "@GeneratorMapping implementation must declare "
                            + "a public no-argument constructor.",
                    declaration);

            return Optional.empty();
        }

        String mappingPackageName =
                processingEnv
                        .getElementUtils()
                        .getPackageOf(declaration)
                        .getQualifiedName()
                        .toString();

        if (!mappingPackageName.equals("mappings")
                && !mappingPackageName.startsWith("mappings.")) {

            printError(
                    "@GeneratorMapping declarations must be placed "
                            + "in the 'mappings' package or one of its subpackages.",
                    declaration);

            return Optional.empty();
        }

        String generatedPackageName =
                "tiles" + mappingPackageName.substring("mappings".length());

        String mappingDeclarationName =
                declaration.getSimpleName().toString();

        String generatedTileBaseName =
                mappingDeclarationName.endsWith("Mapping")
                        ? mappingDeclarationName.substring(
                        0,
                        mappingDeclarationName.length()
                        - "Mapping".length())
                        : mappingDeclarationName;

        String generatedTileName =
                generatedTileBaseName + "GeneratedTile";

        return Optional.of(
                new GeneratorMappingModel(
                        declaration,
                        qualifiedComponentName,
                        namespace,
                        componentName,
                        implementationType,
                        generatedPackageName,
                        generatedTileName));
    }

    private void generateTile(GeneratorMappingModel mapping) {
        try {
            tileWriter.write(mapping);

            processingEnv
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.NOTE,
                            "Generated Tile: "
                                    + mapping.generatedPackageName()
                                    + "."
                                    + mapping.generatedTileName(),
                            mapping.declaration());

        } catch (IOException exception) {
            printError(
                    "Failed to generate Tile for '"
                            + mapping.qualifiedComponentName()
                            + "': "
                            + exception.getMessage(),
                    mapping.declaration());
        }
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