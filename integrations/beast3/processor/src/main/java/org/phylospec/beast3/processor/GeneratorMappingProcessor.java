package org.phylospec.beast3.processor;

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

            processingEnv
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.NOTE,
                            "Discovered generator mapping: "
                                    + element,
                            element);
        }

        return true;
    }
}