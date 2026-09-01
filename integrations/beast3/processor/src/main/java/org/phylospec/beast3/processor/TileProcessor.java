package org.phylospec.beast3.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.phylospec.annotations.GeneratorMapping;

public final class TileProcessor extends AbstractProcessor {

    private TileWriter tileWriter;

    private RegistryWriter registryWriter;

    @Override
    public synchronized void init(
            ProcessingEnvironment processingEnvironment) {

        super.init(processingEnvironment);

        this.tileWriter =
                new TileWriter(
                        processingEnvironment.getFiler());

        this.registryWriter =
                new RegistryWriter(
                        processingEnvironment.getFiler());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                GeneratorMapping.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {

        List<MappingSpec> generatedMappings =
                new ArrayList<>();

        for (Element element :
                roundEnvironment.getElementsAnnotatedWith(
                        GeneratorMapping.class)) {

            if (element.getKind() != ElementKind.INTERFACE) {
                printError(
                        "@GeneratorMapping can only be applied "
                                + "to an interface.",
                        element);
                continue;
            }

            TypeElement declaration =
                    (TypeElement) element;

            readMapping(declaration)
                    .ifPresent(
                            mapping -> {
                                if (generateTile(mapping)) {
                                    generatedMappings.add(mapping);
                                }
                            });
        }

        if (!generatedMappings.isEmpty()) {
            generateRegistry(generatedMappings);
        }

        return true;
    }

    private Optional<MappingSpec> readMapping(
            TypeElement declaration) {

        if (!ElementFilter.methodsIn(
                        declaration.getEnclosedElements())
                .isEmpty()) {

            printError(
                    "Automatic Tile generation currently supports "
                            + "only zero-input mappings. "
                            + "Input mappings are not supported yet.",
                    declaration);

            return Optional.empty();
        }

        AnnotationMirror annotation =
                declaration.getAnnotationMirrors().stream()
                        .filter(this::isGeneratorMapping)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Expected @GeneratorMapping on "
                                                        + declaration
                                                        .getQualifiedName()));

        Map<String, AnnotationValue> values =
                readAnnotationValues(annotation);

        String qualifiedComponentName =
                (String) values
                        .get("component")
                        .getValue();

        TypeMirror implementationType =
                (TypeMirror) values
                        .get("implementation")
                        .getValue();

        if (qualifiedComponentName.isBlank()) {
            printError(
                    "@GeneratorMapping component must not be blank.",
                    declaration);

            return Optional.empty();
        }

        int finalSeparator =
                qualifiedComponentName.lastIndexOf('.');

        if (finalSeparator <= 0
                || finalSeparator
                == qualifiedComponentName.length() - 1) {

            printError(
                    "@GeneratorMapping component must be fully "
                            + "qualified, for example "
                            + "'phylospec.functions.substitution.jc69'.",
                    declaration);

            return Optional.empty();
        }

        String namespace =
                qualifiedComponentName.substring(
                        0,
                        finalSeparator);

        String componentName =
                qualifiedComponentName.substring(
                        finalSeparator + 1);

        Element implementationElement =
                processingEnv
                        .getTypeUtils()
                        .asElement(implementationType);

        if (!(implementationElement
                instanceof TypeElement implementationDeclaration)
                || implementationDeclaration.getKind()
                != ElementKind.CLASS) {

            printError(
                    "@GeneratorMapping implementation must "
                            + "refer to a class.",
                    declaration);

            return Optional.empty();
        }

        if (implementationDeclaration
                .getModifiers()
                .contains(Modifier.ABSTRACT)) {

            printError(
                    "@GeneratorMapping implementation must "
                            + "not be abstract.",
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

        if (!hasPublicNoArgumentConstructor(
                implementationDeclaration)) {

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
                    "@GeneratorMapping declarations must be "
                            + "placed in the 'mappings' package "
                            + "or one of its subpackages.",
                    declaration);

            return Optional.empty();
        }

        String generatedPackageName =
                "tiles"
                        + mappingPackageName.substring(
                        "mappings".length());

        String declarationName =
                declaration.getSimpleName().toString();

        String tileBaseName =
                declarationName.endsWith("Mapping")
                        ? declarationName.substring(
                        0,
                        declarationName.length()
                        - "Mapping".length())
                        : declarationName;

        String generatedTileName =
                tileBaseName + "GeneratedTile";

        return Optional.of(
                new MappingSpec(
                        declaration,
                        qualifiedComponentName,
                        namespace,
                        componentName,
                        implementationType,
                        generatedPackageName,
                        generatedTileName));
    }

    private Map<String, AnnotationValue> readAnnotationValues(
            AnnotationMirror annotation) {

        Map<String, AnnotationValue> values =
                new HashMap<>();

        for (Map.Entry<
                ? extends ExecutableElement,
                ? extends AnnotationValue>
                entry :
                processingEnv
                        .getElementUtils()
                        .getElementValuesWithDefaults(annotation)
                        .entrySet()) {

            String name =
                    entry.getKey()
                            .getSimpleName()
                            .toString();

            values.put(name, entry.getValue());
        }

        return values;
    }

    private boolean hasPublicNoArgumentConstructor(
            TypeElement implementation) {

        return ElementFilter.constructorsIn(
                        implementation.getEnclosedElements())
                .stream()
                .anyMatch(
                        constructor ->
                                constructor
                                        .getParameters()
                                        .isEmpty()
                                        && constructor
                                        .getModifiers()
                                        .contains(
                                                Modifier.PUBLIC));
    }

    private boolean generateTile(
            MappingSpec mapping) {

        try {
            tileWriter.write(mapping);

            printNote(
                    "Generated Tile: "
                            + mapping.generatedPackageName()
                            + "."
                            + mapping.generatedTileName(),
                    mapping.declaration());

            return true;

        } catch (IOException exception) {
            printError(
                    "Failed to generate Tile for '"
                            + mapping.qualifiedComponentName()
                            + "': "
                            + exception.getMessage(),
                    mapping.declaration());

            return false;
        }
    }

    private void generateRegistry(
            List<MappingSpec> mappings) {

        try {
            registryWriter.write(mappings);

            printNote(
                    "Generated Tile registry: "
                            + RegistryWriter
                            .GENERATED_PACKAGE
                            + "."
                            + RegistryWriter
                            .GENERATED_CLASS,
                    mappings.get(0).declaration());

        } catch (IOException exception) {
            printError(
                    "Failed to generate Tile registry: "
                            + exception.getMessage(),
                    mappings.get(0).declaration());
        }
    }

    private boolean isGeneratorMapping(
            AnnotationMirror annotation) {

        Element annotationElement =
                annotation
                        .getAnnotationType()
                        .asElement();

        return annotationElement
                instanceof TypeElement annotationType
                && annotationType
                .getQualifiedName()
                .contentEquals(
                        GeneratorMapping.class
                                .getCanonicalName());
    }

    private void printNote(
            String message,
            Element element) {

        processingEnv
                .getMessager()
                .printMessage(
                        Diagnostic.Kind.NOTE,
                        message,
                        element);
    }

    private void printError(
            String message,
            Element element) {

        processingEnv
                .getMessager()
                .printMessage(
                        Diagnostic.Kind.ERROR,
                        message,
                        element);
    }
}