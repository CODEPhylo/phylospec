package org.phylospec.beast3.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.phylospec.tiling.TypeAdapter;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import org.phylospec.components.Argument;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Generator;

public final class TileProcessor extends AbstractProcessor {

    private TileWriter tileWriter;
    private RegistryWriter registryWriter;
    private ComponentResolver componentResolver;
    private final List<MappingSpec> generatedMappings =
            new ArrayList<>();

    private boolean registryGenerated;

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

        try {
            this.componentResolver =
                    new ComponentResolver(
                            ComponentResolver
                                    .loadCoreComponentLibraries());

        } catch (IOException exception) {
            processingEnvironment
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            "Could not load the PhyloSpec component library: "
                                    + exception.getMessage());
        }
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

        if (componentResolver == null) {
            return true;
        }

        if (roundEnvironment.processingOver()) {
            return true;
        }

        boolean generatedTileThisRound = false;

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

            Optional<MappingSpec> mappingResult =
                    readMapping(declaration);

            if (mappingResult.isEmpty()) {
                continue;
            }

            MappingSpec mapping =
                    mappingResult.orElseThrow();

            if (generateTile(mapping)) {
                generatedMappings.add(mapping);
                generatedTileThisRound = true;
            }
        }

        if (!generatedTileThisRound
                && !generatedMappings.isEmpty()
                && !registryGenerated) {

            generateRegistry(
                    List.copyOf(generatedMappings));

            registryGenerated = true;
        }

        return true;
    }

    private Optional<MappingSpec> readMapping(
            TypeElement declaration) {

        AnnotationMirror annotation =
                findAnnotation(
                        declaration,
                        GeneratorMapping.class
                                .getCanonicalName())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Expected @GeneratorMapping on "
                                                        + declaration
                                                        .getQualifiedName()));

        Map<String, AnnotationValue> values =
                readAnnotationValues(annotation);

        String qualifiedComponentName =
                (String)
                        values.get("component")
                                .getValue();

        TypeMirror implementationType =
                (TypeMirror)
                        values.get("implementation")
                                .getValue();

        TypeMirror declaredOutputType =
                (TypeMirror)
                        values.get("output")
                                .getValue();

        List<String> declaredArguments =
                readStringArray(
                        values.get("arguments"));

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

        Optional<TypeElement> implementationResult =
                validateImplementation(
                        implementationType,
                        declaration);

        if (implementationResult.isEmpty()) {
            return Optional.empty();
        }

        TypeElement implementationDeclaration =
                implementationResult.orElseThrow();

        TypeMirror outputType =
                resolveOutputType(
                        implementationType,
                        declaredOutputType);

        if (!processingEnv
                .getTypeUtils()
                .isAssignable(
                        implementationType,
                        outputType)) {

            printError(
                    "BEAST implementation type '"
                            + implementationType
                            + "' cannot be returned as output type '"
                            + outputType
                            + "'.",
                    declaration);

            return Optional.empty();
        }

        List<Generator> componentGenerators =
                componentResolver.resolveGenerator(
                        qualifiedComponentName);

        if (componentGenerators.isEmpty()) {
            printError(
                    "Unknown PhyloSpec component '"
                            + qualifiedComponentName
                            + "'.",
                    declaration);

            return Optional.empty();
        }

        Optional<List<Generator>> selectedGeneratorsResult =
                selectComponentGenerators(
                        qualifiedComponentName,
                        componentGenerators,
                        declaredArguments,
                        declaration);

        if (selectedGeneratorsResult.isEmpty()) {
            return Optional.empty();
        }

        List<Generator> selectedGenerators =
                selectedGeneratorsResult.orElseThrow();

        Optional<List<InputSpec>> inputResult =
                readInputs(
                        declaration,
                        implementationDeclaration,
                        implementationType,
                        selectedGenerators);

        if (inputResult.isEmpty()) {
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
                        outputType,
                        inputResult.orElseThrow(),
                        generatedPackageName,
                        generatedTileName));
    }

    private Optional<TypeElement> validateImplementation(
            TypeMirror implementationType,
            TypeElement mappingDeclaration) {

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
                    mappingDeclaration);

            return Optional.empty();
        }

        if (implementationDeclaration
                .getModifiers()
                .contains(Modifier.ABSTRACT)) {

            printError(
                    "@GeneratorMapping implementation must "
                            + "not be abstract.",
                    mappingDeclaration);

            return Optional.empty();
        }

        if (!implementationDeclaration
                .getModifiers()
                .contains(Modifier.PUBLIC)) {

            printError(
                    "@GeneratorMapping implementation must be public.",
                    mappingDeclaration);

            return Optional.empty();
        }

        if (!hasPublicNoArgumentConstructor(
                implementationDeclaration)) {

            printError(
                    "@GeneratorMapping implementation must declare "
                            + "a public no-argument constructor.",
                    mappingDeclaration);

            return Optional.empty();
        }

        return Optional.of(implementationDeclaration);
    }

    private TypeMirror resolveOutputType(
            TypeMirror implementationType,
            TypeMirror declaredOutputType) {

        Elements elements =
                processingEnv.getElementUtils();

        Types types =
                processingEnv.getTypeUtils();

        TypeElement voidClass =
                elements.getTypeElement(
                        Void.class.getCanonicalName());

        boolean usesDefaultOutput =
                types.isSameType(
                        types.erasure(declaredOutputType),
                        types.erasure(voidClass.asType()));

        return usesDefaultOutput
                ? implementationType
                : declaredOutputType;
    }

    private Optional<List<InputSpec>> readInputs(
            TypeElement mappingDeclaration,
            TypeElement implementationDeclaration,
            TypeMirror implementationType,
            List<Generator> componentGenerators) {

        List<InputSpec> inputs =
                new ArrayList<>();

        Set<String> usedArguments =
                new HashSet<>();

        Set<String> usedBeastInputs =
                new HashSet<>();

        for (ExecutableElement method :
                ElementFilter.methodsIn(
                        mappingDeclaration.getEnclosedElements())) {

            Optional<? extends AnnotationMirror> annotationResult =
                    findAnnotation(
                            method,
                            InputMapping.class.getCanonicalName());

            if (annotationResult.isEmpty()) {
                printError(
                        "Every method in a @GeneratorMapping "
                                + "interface must declare @InputMapping.",
                        method);
                return Optional.empty();
            }

            if (!method.getParameters().isEmpty()) {
                printError(
                        "@InputMapping methods must not declare parameters.",
                        method);
                return Optional.empty();
            }

            if (method.getReturnType().getKind()
                    == TypeKind.VOID) {

                printError(
                        "@InputMapping methods must return "
                                + "the Java value type passed to BEAST.",
                        method);
                return Optional.empty();
            }

            if (method.getModifiers().contains(Modifier.DEFAULT)
                    || method.getModifiers().contains(Modifier.STATIC)) {

                printError(
                        "@InputMapping methods must be abstract "
                                + "interface methods.",
                        method);
                return Optional.empty();
            }

            Map<String, AnnotationValue> values =
                    readAnnotationValues(
                            annotationResult.orElseThrow());

            String argumentName =
                    (String)
                            values.get("argument")
                                    .getValue();

            String beastInputName =
                    (String)
                            values.get("input")
                                    .getValue();

            TypeMirror adapterType =
                    (TypeMirror)
                            values.get("adapter")
                                    .getValue();

            if (argumentName.isBlank()) {
                printError(
                        "@InputMapping argument must not be blank.",
                        method);
                return Optional.empty();
            }

            if (beastInputName.isBlank()) {
                printError(
                        "@InputMapping input must not be blank.",
                        method);
                return Optional.empty();
            }

            if (!usedArguments.add(argumentName)) {
                printError(
                        "PhyloSpec argument '"
                                + argumentName
                                + "' is mapped more than once.",
                        method);
                return Optional.empty();
            }

            if (!usedBeastInputs.add(beastInputName)) {
                printError(
                        "BEAST input '"
                                + beastInputName
                                + "' is mapped more than once.",
                        method);
                return Optional.empty();
            }

            Optional<Boolean> requiredResult =
                    resolveRequired(
                            argumentName,
                            componentGenerators,
                            method);

            if (requiredResult.isEmpty()) {
                return Optional.empty();
            }

            TypeMirror valueType =
                    method.getReturnType();

            Optional<TypeMirror> inputTypeResult =
                    resolveBeastInputType(
                            implementationDeclaration,
                            implementationType,
                            beastInputName,
                            method);

            if (inputTypeResult.isEmpty()) {
                return Optional.empty();
            }

            TypeMirror inputType =
                    inputTypeResult.orElseThrow();

            boolean usesAdapter =
                    !isVoidType(adapterType);

            if (usesAdapter) {
                if (!validateAdapter(
                        adapterType,
                        valueType,
                        inputType,
                        method)) {

                    return Optional.empty();
                }
            } else if (!processingEnv
                    .getTypeUtils()
                    .isAssignable(
                            valueType,
                            inputType)) {

                printError(
                        "PhyloSpec argument produces Java type '"
                                + valueType
                                + "', but BEAST input '"
                                + beastInputName
                                + "' expects '"
                                + inputType
                                + "'.",
                        method);

                return Optional.empty();
            }

            inputs.add(
                    new InputSpec(
                            method,
                            argumentName,
                            beastInputName,
                            valueType,
                            inputType,
                            adapterType,
                            usesAdapter,
                            requiredResult.orElseThrow()));
        }

        List<String> missingRequiredArguments =
                componentGenerators.stream()
                        .flatMap(
                                generator ->
                                        generator.getArguments()
                                                .stream())
                        .filter(
                                argument ->
                                        Boolean.TRUE.equals(
                                                argument.getRequired()))
                        .map(Argument::getName)
                        .distinct()
                        .filter(
                                argumentName ->
                                        !usedArguments.contains(
                                                argumentName))
                        .sorted()
                        .toList();

        if (!missingRequiredArguments.isEmpty()) {
            printError(
                    "Missing mappings for required PhyloSpec "
                            + "arguments: '"
                            + String.join(
                            "', '",
                            missingRequiredArguments)
                            + "'.",
                    mappingDeclaration);

            return Optional.empty();
        }

        return Optional.of(inputs);
    }

    private Optional<Boolean> resolveRequired(
            String argumentName,
            List<Generator> componentGenerators,
            Element declaration) {

        List<Argument> matchingArguments =
                componentGenerators.stream()
                        .flatMap(
                                generator ->
                                        generator.getArguments()
                                                .stream())
                        .filter(
                                argument ->
                                        argumentName.equals(
                                                argument.getName()))
                        .toList();

        if (matchingArguments.isEmpty()) {
            printError(
                    "Unknown PhyloSpec argument '"
                            + argumentName
                            + "'.",
                    declaration);

            return Optional.empty();
        }

        Set<Boolean> requiredValues =
                new HashSet<>();

        for (Argument argument : matchingArguments) {
            requiredValues.add(
                    Boolean.TRUE.equals(
                            argument.getRequired()));
        }

        if (requiredValues.size() > 1) {
            printError(
                    "PhyloSpec argument '"
                            + argumentName
                            + "' has conflicting required status "
                            + "across component overloads.",
                    declaration);

            return Optional.empty();
        }

        return Optional.of(
                requiredValues.iterator().next());
    }

    private Optional<TypeMirror> resolveBeastInputType(
            TypeElement implementationDeclaration,
            TypeMirror implementationType,
            String beastInputName,
            Element mappingDeclaration) {

        Elements elements =
                processingEnv.getElementUtils();

        Types types =
                processingEnv.getTypeUtils();

        Optional<VariableElement> fieldResult =
                ElementFilter.fieldsIn(
                                elements.getAllMembers(
                                        implementationDeclaration))
                        .stream()
                        .filter(
                                field ->
                                        field.getSimpleName()
                                                .contentEquals(
                                                        beastInputName))
                        .findFirst();

        if (fieldResult.isEmpty()) {
            printError(
                    "BEAST implementation '"
                            + implementationType
                            + "' has no input field named '"
                            + beastInputName
                            + "'.",
                    mappingDeclaration);

            return Optional.empty();
        }

        VariableElement field =
                fieldResult.orElseThrow();

        if (!field.getModifiers().contains(Modifier.PUBLIC)) {
            printError(
                    "BEAST input field '"
                            + beastInputName
                            + "' must be public.",
                    mappingDeclaration);

            return Optional.empty();
        }

        if (field.getModifiers().contains(Modifier.STATIC)) {
            printError(
                    "BEAST input field '"
                            + beastInputName
                            + "' must not be static.",
                    mappingDeclaration);

            return Optional.empty();
        }

        TypeMirror fieldType =
                types.asMemberOf(
                        (DeclaredType) implementationType,
                        field);

        TypeElement beastInputClass =
                elements.getTypeElement(
                        "beast.base.core.Input");

        if (beastInputClass == null) {
            printError(
                    "Could not resolve beast.base.core.Input.",
                    mappingDeclaration);

            return Optional.empty();
        }

        if (!(fieldType instanceof DeclaredType declaredFieldType)
                || !types.isAssignable(
                types.erasure(fieldType),
                types.erasure(
                        beastInputClass.asType()))) {

            printError(
                    "Field '"
                            + beastInputName
                            + "' is not a beast.base.core.Input.",
                    mappingDeclaration);

            return Optional.empty();
        }

        if (declaredFieldType.getTypeArguments().size() != 1) {
            printError(
                    "BEAST input field '"
                            + beastInputName
                            + "' must declare one value type.",
                    mappingDeclaration);

            return Optional.empty();
        }

        return Optional.of(
                declaredFieldType
                        .getTypeArguments()
                        .getFirst());
    }

    private Optional<? extends AnnotationMirror> findAnnotation(
            Element element,
            String annotationName) {

        return element.getAnnotationMirrors()
                .stream()
                .filter(
                        annotation -> {
                            Element annotationElement =
                                    annotation
                                            .getAnnotationType()
                                            .asElement();

                            return annotationElement
                                    instanceof TypeElement annotationType
                                    && annotationType
                                    .getQualifiedName()
                                    .contentEquals(
                                            annotationName);
                        })
                .findFirst();
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

            values.put(
                    entry.getKey()
                            .getSimpleName()
                            .toString(),
                    entry.getValue());
        }

        return values;
    }

    private List<String> readStringArray(
            AnnotationValue annotationValue) {

        Object value = annotationValue.getValue();

        if (!(value instanceof List<?> entries)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for (Object entry : entries) {
            AnnotationValue argumentValue =
                    (AnnotationValue) entry;

            result.add(
                    (String) argumentValue.getValue());
        }

        return List.copyOf(result);
    }

    private Optional<List<Generator>> selectComponentGenerators(
            String qualifiedComponentName,
            List<Generator> componentGenerators,
            List<String> declaredArguments,
            Element declaration) {

        if (componentGenerators.size() == 1
                && declaredArguments.isEmpty()) {

            return Optional.of(componentGenerators);
        }

        if (declaredArguments.isEmpty()) {
            printError(
                    "PhyloSpec component '"
                            + qualifiedComponentName
                            + "' has multiple overloads. "
                            + "Select one using "
                            + "@GeneratorMapping arguments.",
                    declaration);

            return Optional.empty();
        }

        if (new HashSet<>(declaredArguments).size()
                != declaredArguments.size()) {

            printError(
                    "@GeneratorMapping arguments must not "
                            + "contain duplicate names.",
                    declaration);

            return Optional.empty();
        }

        List<Generator> matchingGenerators =
                componentGenerators.stream()
                        .filter(
                                generator ->
                                        generator.getArguments()
                                                .stream()
                                                .map(Argument::getName)
                                                .toList()
                                                .equals(
                                                        declaredArguments))
                        .toList();

        if (matchingGenerators.isEmpty()) {
            String availableSignatures =
                    componentGenerators.stream()
                            .map(
                                    generator ->
                                            generator.getArguments()
                                                    .stream()
                                                    .map(Argument::getName)
                                                    .toList()
                                                    .toString())
                            .distinct()
                            .sorted()
                            .reduce(
                                    (left, right) ->
                                            left + ", " + right)
                            .orElse("[]");

            printError(
                    "No overload of PhyloSpec component '"
                            + qualifiedComponentName
                            + "' has argument signature "
                            + declaredArguments
                            + ". Available signatures: "
                            + availableSignatures
                            + ".",
                    declaration);

            return Optional.empty();
        }

        if (matchingGenerators.size() > 1) {
            printError(
                    "Argument signature "
                            + declaredArguments
                            + " does not uniquely identify an overload "
                            + "of PhyloSpec component '"
                            + qualifiedComponentName
                            + "'.",
                    declaration);

            return Optional.empty();
        }

        return Optional.of(matchingGenerators);
    }

    private boolean validateAdapter(
            TypeMirror adapterType,
            TypeMirror valueType,
            TypeMirror inputType,
            Element mappingDeclaration) {

        Types types =
                processingEnv.getTypeUtils();

        Elements elements =
                processingEnv.getElementUtils();

        Element adapterElement =
                types.asElement(adapterType);

        if (!(adapterElement
                instanceof TypeElement adapterDeclaration)
                || adapterDeclaration.getKind()
                != ElementKind.CLASS) {

            printError(
                    "@InputMapping adapter must refer to a class.",
                    mappingDeclaration);

            return false;
        }

        if (!adapterDeclaration
                .getModifiers()
                .contains(Modifier.PUBLIC)) {

            printError(
                    "@InputMapping adapter must be public.",
                    mappingDeclaration);

            return false;
        }

        if (adapterDeclaration
                .getModifiers()
                .contains(Modifier.ABSTRACT)) {

            printError(
                    "@InputMapping adapter must not be abstract.",
                    mappingDeclaration);

            return false;
        }

        if (!hasPublicNoArgumentConstructor(
                adapterDeclaration)) {

            printError(
                    "@InputMapping adapter must declare "
                            + "a public no-argument constructor.",
                    mappingDeclaration);

            return false;
        }

        TypeElement adapterInterface =
                elements.getTypeElement(
                        TypeAdapter.class.getCanonicalName());

        if (adapterInterface == null) {
            printError(
                    "Could not resolve "
                            + TypeAdapter.class.getCanonicalName()
                            + ".",
                    mappingDeclaration);

            return false;
        }

        Optional<DeclaredType> adapterSupertypeResult =
                findDeclaredSupertype(
                        adapterType,
                        adapterInterface.asType());

        if (adapterSupertypeResult.isEmpty()) {
            printError(
                    "Adapter '"
                            + adapterType
                            + "' must implement "
                            + TypeAdapter.class.getCanonicalName()
                            + ".",
                    mappingDeclaration);

            return false;
        }

        List<? extends TypeMirror> typeArguments =
                adapterSupertypeResult
                        .orElseThrow()
                        .getTypeArguments();

        if (typeArguments.size() != 3) {
            printError(
                    "Adapter '"
                            + adapterType
                            + "' must declare source, target, "
                            + "and state types.",
                    mappingDeclaration);

            return false;
        }

        TypeMirror adapterSourceType =
                typeArguments.get(0);

        TypeMirror adapterTargetType =
                typeArguments.get(1);

        TypeMirror adapterStateType =
                typeArguments.get(2);

        if (!types.isAssignable(
                valueType,
                adapterSourceType)) {

            printError(
                    "Adapter '"
                            + adapterType
                            + "' accepts source type '"
                            + adapterSourceType
                            + "', but the PhyloSpec argument "
                            + "produces '"
                            + valueType
                            + "'.",
                    mappingDeclaration);

            return false;
        }

        if (!types.isAssignable(
                adapterTargetType,
                inputType)) {

            printError(
                    "Adapter '"
                            + adapterType
                            + "' produces target type '"
                            + adapterTargetType
                            + "', but the BEAST input expects '"
                            + inputType
                            + "'.",
                    mappingDeclaration);

            return false;
        }

        TypeElement beastState =
                elements.getTypeElement(
                        "beastconfig.BEASTState");

        if (beastState == null) {
            printError(
                    "Could not resolve beastconfig.BEASTState.",
                    mappingDeclaration);

            return false;
        }

        if (!types.isAssignable(
                beastState.asType(),
                adapterStateType)) {

            printError(
                    "Adapter '"
                            + adapterType
                            + "' cannot accept BEASTState as "
                            + "its engine state.",
                    mappingDeclaration);

            return false;
        }

        return true;
    }

    private Optional<DeclaredType> findDeclaredSupertype(
            TypeMirror candidateType,
            TypeMirror expectedType) {

        Types types =
                processingEnv.getTypeUtils();

        if (candidateType
                instanceof DeclaredType declaredCandidate
                && types.isSameType(
                types.erasure(candidateType),
                types.erasure(expectedType))) {

            return Optional.of(
                    declaredCandidate);
        }

        for (TypeMirror supertype :
                types.directSupertypes(candidateType)) {

            Optional<DeclaredType> result =
                    findDeclaredSupertype(
                            supertype,
                            expectedType);

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    private boolean isVoidType(
            TypeMirror type) {

        Types types =
                processingEnv.getTypeUtils();

        TypeElement voidClass =
                processingEnv
                        .getElementUtils()
                        .getTypeElement(
                                Void.class.getCanonicalName());

        return voidClass != null
                && types.isSameType(
                types.erasure(type),
                types.erasure(
                        voidClass.asType()));
    }

    private boolean hasPublicNoArgumentConstructor(
            TypeElement implementation) {

        return ElementFilter.constructorsIn(
                        implementation
                                .getEnclosedElements())
                .stream()
                .anyMatch(
                        constructor ->
                                constructor.getParameters()
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
                            + RegistryWriter.GENERATED_PACKAGE
                            + "."
                            + RegistryWriter.GENERATED_CLASS,
                    mappings.getFirst()
                            .declaration());

        } catch (IOException exception) {
            printError(
                    "Failed to generate Tile registry: "
                            + exception.getMessage(),
                    mappings.getFirst()
                            .declaration());
        }
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