package org.phylospec.beast3.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

final class AdapterIndexWriter {

    static final String GENERATED_CLASS = "GeneratedAdapterIndex";

    private final Filer filer;
    private final String generatedPackage;

    AdapterIndexWriter(Filer filer, String generatedPackage) {
        this.filer = filer;
        this.generatedPackage = generatedPackage;
    }

    void write(List<AdapterSpec> adapters) throws IOException {
        String qualifiedName = generatedPackage + "." + GENERATED_CLASS;
        Element[] origins =
                adapters.stream()
                        .map(AdapterSpec::declaration)
                        .toArray(Element[]::new);

        JavaFileObject source = filer.createSourceFile(qualifiedName, origins);
        try (Writer writer = source.openWriter()) {
            writer.write(generateSource(adapters));
        }
    }

    private String generateSource(List<AdapterSpec> adapters) {
        String adapterTypes =
                adapters.stream()
                        .map(adapter -> adapter.declaration().getQualifiedName().toString())
                        .sorted(Comparator.naturalOrder())
                        .map(name -> "        " + name + ".class")
                        .collect(Collectors.joining(",\n"));

        return """
                package %s;

                @org.phylospec.annotations.AdapterLibrary({
                %s
                })
                public final class GeneratedAdapterIndex {

                    private GeneratedAdapterIndex() {}
                }
                """
                .formatted(generatedPackage, adapterTypes);
    }
}
