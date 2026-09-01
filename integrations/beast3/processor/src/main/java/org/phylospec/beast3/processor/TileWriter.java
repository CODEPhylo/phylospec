package org.phylospec.beast3.processor;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

final class TileWriter {

    private final Filer filer;

    TileWriter(Filer filer) {
        this.filer = filer;
    }

    void write(MappingSpec mapping) throws IOException {
        String generatedQualifiedName =
                mapping.generatedPackageName()
                        + "."
                        + mapping.generatedTileName();

        JavaFileObject sourceFile =
                filer.createSourceFile(
                        generatedQualifiedName,
                        mapping.declaration());

        try (Writer writer = sourceFile.openWriter()) {
            writer.write(generateSource(mapping));
        }
    }

    private String generateSource(MappingSpec mapping) {
        String implementationType =
                mapping.implementationType().toString();

        return """
                package %s;

                public final class %s
                        extends org.phylospec.tiling.tiles.GeneratorTile<
                                %s,
                                beastconfig.BEASTState> {

                    @Override
                    public String getPhyloSpecGeneratorName() {
                        return "%s";
                    }

                    @Override
                    public java.util.Optional<String> getNamespace() {
                        return java.util.Optional.of("%s");
                    }

                    @Override
                    public %s applyTile(
                            beastconfig.BEASTState beastState,
                            java.util.IdentityHashMap<
                                    org.phylospec.ast.Expr.Variable,
                                    Integer> indexVariables) {

                        return new %s();
                    }
                }
                """
                .formatted(
                        mapping.generatedPackageName(),
                        mapping.generatedTileName(),
                        implementationType,
                        mapping.componentName(),
                        mapping.namespace(),
                        implementationType,
                        implementationType);
    }
}