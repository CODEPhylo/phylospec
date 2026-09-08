package org.phylospec.beast3.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.phylospec.annotations.AdapterMapping;
import org.phylospec.tiling.TypeAdapter;

final class AdapterRegistry {

    private final ProcessingEnvironment processingEnvironment;
    private final List<AdapterSpec> adapters = new ArrayList<>();

    AdapterRegistry(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    void register(RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(AdapterMapping.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                printError("@AdapterMapping can only be applied to a class.", element);
                continue;
            }

            TypeElement declaration = (TypeElement) element;
            Optional<AdapterSpec> adapterResult = read(declaration);
            if (adapterResult.isEmpty()) {
                continue;
            }

            AdapterSpec adapter = adapterResult.orElseThrow();
            Optional<AdapterSpec> duplicate =
                    adapters.stream()
                            .filter(existing -> sameConversion(existing, adapter))
                            .findFirst();

            if (duplicate.isPresent()) {
                printError(
                        "Duplicate @AdapterMapping for Java conversion '"
                                + adapter.sourceType()
                                + "' to '"
                                + adapter.targetType()
                                + "'. It is already declared by '"
                                + duplicate.orElseThrow().declaration().getQualifiedName()
                                + "'.",
                        declaration);
                continue;
            }

            adapters.add(adapter);
        }
    }

    Optional<TypeMirror> resolve(
            TypeMirror sourceType,
            TypeMirror targetType,
            String beastInputName,
            Element mappingDeclaration) {

        Types types = processingEnvironment.getTypeUtils();
        List<AdapterSpec> candidates =
                adapters.stream()
                        .filter(adapter -> types.isAssignable(sourceType, adapter.sourceType()))
                        .filter(adapter -> types.isAssignable(adapter.targetType(), targetType))
                        .toList();

        if (candidates.isEmpty()) {
            printError(
                    "PhyloSpec argument produces Java type '"
                            + sourceType
                            + "', but BEAST input '"
                            + beastInputName
                            + "' expects '"
                            + targetType
                            + "', and no registered adapter can convert between them.",
                    mappingDeclaration);
            return Optional.empty();
        }

        if (candidates.size() > 1) {
            String adapterNames =
                    candidates.stream()
                            .map(adapter -> adapter.declaration().getQualifiedName().toString())
                            .sorted()
                            .reduce((left, right) -> left + ", " + right)
                            .orElseThrow();

            printError(
                    "Multiple registered adapters can convert Java type '"
                            + sourceType
                            + "' to BEAST input type '"
                            + targetType
                            + "': "
                            + adapterNames
                            + ". Specify @InputMapping adapter explicitly.",
                    mappingDeclaration);
            return Optional.empty();
        }

        return Optional.of(candidates.getFirst().adapterType());
    }

    private Optional<AdapterSpec> read(TypeElement declaration) {
        if (!declaration.getModifiers().contains(Modifier.PUBLIC)) {
            printError("@AdapterMapping class must be public.", declaration);
            return Optional.empty();
        }

        if (declaration.getModifiers().contains(Modifier.ABSTRACT)) {
            printError("@AdapterMapping class must not be abstract.", declaration);
            return Optional.empty();
        }

        if (!hasPublicNoArgumentConstructor(declaration)) {
            printError(
                    "@AdapterMapping class must declare a public no-argument constructor.",
                    declaration);
            return Optional.empty();
        }

        Types types = processingEnvironment.getTypeUtils();
        Elements elements = processingEnvironment.getElementUtils();
        TypeElement adapterInterface = elements.getTypeElement(TypeAdapter.class.getCanonicalName());

        if (adapterInterface == null) {
            printError("Could not resolve " + TypeAdapter.class.getCanonicalName() + ".", declaration);
            return Optional.empty();
        }

        Optional<DeclaredType> adapterSupertypeResult =
                findDeclaredSupertype(declaration.asType(), adapterInterface.asType());

        if (adapterSupertypeResult.isEmpty()) {
            printError(
                    "@AdapterMapping class must implement "
                            + TypeAdapter.class.getCanonicalName()
                            + ".",
                    declaration);
            return Optional.empty();
        }

        List<? extends TypeMirror> typeArguments =
                adapterSupertypeResult.orElseThrow().getTypeArguments();

        if (typeArguments.size() != 3) {
            printError(
                    "@AdapterMapping class must declare source, target, and state types.",
                    declaration);
            return Optional.empty();
        }

        TypeElement beastState = elements.getTypeElement("beastconfig.BEASTState");
        if (beastState == null) {
            printError("Could not resolve beastconfig.BEASTState.", declaration);
            return Optional.empty();
        }

        TypeMirror stateType = typeArguments.get(2);
        if (!types.isAssignable(beastState.asType(), stateType)) {
            printError(
                    "@AdapterMapping class cannot accept BEASTState as its engine state.",
                    declaration);
            return Optional.empty();
        }

        return Optional.of(
                new AdapterSpec(
                        declaration,
                        declaration.asType(),
                        typeArguments.get(0),
                        typeArguments.get(1)));
    }

    private boolean sameConversion(AdapterSpec left, AdapterSpec right) {
        Types types = processingEnvironment.getTypeUtils();
        return types.isSameType(left.sourceType(), right.sourceType())
                && types.isSameType(left.targetType(), right.targetType());
    }

    private boolean hasPublicNoArgumentConstructor(TypeElement declaration) {
        List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(declaration.getEnclosedElements());

        if (constructors.isEmpty()) {
            return declaration.getModifiers().contains(Modifier.PUBLIC);
        }

        return constructors.stream()
                .anyMatch(
                        constructor ->
                                constructor.getParameters().isEmpty()
                                        && constructor.getModifiers().contains(Modifier.PUBLIC));
    }

    private Optional<DeclaredType> findDeclaredSupertype(
            TypeMirror candidateType, TypeMirror expectedType) {
        Types types = processingEnvironment.getTypeUtils();

        if (candidateType instanceof DeclaredType declaredCandidate
                && types.isSameType(types.erasure(candidateType), types.erasure(expectedType))) {
            return Optional.of(declaredCandidate);
        }

        for (TypeMirror supertype : types.directSupertypes(candidateType)) {
            Optional<DeclaredType> result = findDeclaredSupertype(supertype, expectedType);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    private void printError(String message, Element element) {
        processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
