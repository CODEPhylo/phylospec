package org.phylospec.beast3.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

final class RegistryWriter {

    static final String GENERATED_CLASS =
            "GeneratedTileRegistry";

    private final Filer filer;
    private final String generatedPackage;

    RegistryWriter(
            Filer filer,
            String generatedPackage) {

        this.filer = filer;
        this.generatedPackage = generatedPackage;
    }

    void write(List<MappingSpec> mappings)
            throws IOException {

        String generatedQualifiedName =
                generatedPackage
                        + "."
                        + GENERATED_CLASS;

        Element[] originatingElements =
                mappings.stream()
                        .map(MappingSpec::declaration)
                        .toArray(Element[]::new);

        JavaFileObject sourceFile =
                filer.createSourceFile(
                        generatedQualifiedName,
                        originatingElements);

        try (Writer writer = sourceFile.openWriter()) {
            writer.write(generateSource(mappings));
        }
    }

    private String generateSource(
            List<MappingSpec> mappings) {

        String tileInstances =
                mappings.stream()
                        .sorted(
                                Comparator.comparing(
                                        MappingSpec
                                                ::qualifiedComponentName))
                        .map(this::createTileExpression)
                        .collect(
                                Collectors.joining(",\n"));

        return """
                package %s;

                public final class GeneratedTileRegistry {

                    private GeneratedTileRegistry() {}

                    public static java.util.List<
                                    org.phylospec.tiling.tiles.CandidateTile<
                                            beastconfig.BEASTState>>
                            createTiles() {

                        return java.util.List
                                .<org.phylospec.tiling.tiles.CandidateTile<
                                        beastconfig.BEASTState>>
                                        of(
                %s
                                        );
                    }
                }
                """
                .formatted(
                        generatedPackage,
                        tileInstances);
    }

    private String createTileExpression(
            MappingSpec mapping) {

        return "                                                new "
                + mapping.generatedPackageName()
                + "."
                + mapping.generatedTileName()
                + "()";
    }
}
