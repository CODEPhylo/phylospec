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

    void write(MappingSpec mapping)
            throws IOException {

        String generatedQualifiedName =
                mapping.generatedPackageName()
                        + "."
                        + mapping.generatedTileName();

        JavaFileObject sourceFile =
                filer.createSourceFile(
                        generatedQualifiedName,
                        mapping.declaration());

        try (Writer writer =
                     sourceFile.openWriter()) {

            writer.write(
                    generateSource(mapping));
        }
    }

    private String generateSource(
            MappingSpec mapping) {

        StringBuilder source =
                new StringBuilder();

        source.append("package ")
                .append(mapping.generatedPackageName())
                .append(";\n\n");

        source.append("public final class ")
                .append(mapping.generatedTileName())
                .append("\n")
                .append("        extends ")
                .append("org.phylospec.tiling.tiles.GeneratorTile<\n")
                .append("                ")
                .append(mapping.outputType())
                .append(",\n")
                .append("                beastconfig.BEASTState> {\n\n");

        appendInputFields(
                source,
                mapping);

        source.append("    @Override\n")
                .append("    public String ")
                .append("getPhyloSpecGeneratorName() {\n")
                .append("        return ")
                .append(javaString(mapping.componentName()))
                .append(";\n")
                .append("    }\n\n");

        source.append("    @Override\n")
                .append("    public java.util.Optional<String> ")
                .append("getNamespace() {\n")
                .append("        return java.util.Optional.of(")
                .append(javaString(mapping.namespace()))
                .append(");\n")
                .append("    }\n\n");

        appendApplyMethod(
                source,
                mapping);

        source.append("}\n");

        return source.toString();
    }

    private void appendInputFields(
            StringBuilder source,
            MappingSpec mapping) {

        for (InputSpec input : mapping.inputs()) {
            source.append("    final ")
                    .append(
                            "org.phylospec.tiling.tiles."
                                    + "GeneratorTile.GeneratorTileInput<\n")
                    .append("            ")
                    .append(input.valueType())
                    .append(",\n")
                    .append("            beastconfig.BEASTState> ")
                    .append(fieldName(input))
                    .append(" =\n")
                    .append("                    new ")
                    .append(
                            "org.phylospec.tiling.tiles."
                                    + "GeneratorTile.GeneratorTileInput<>(\n")
                    .append("                            ")
                    .append(javaString(input.argument()))
                    .append(",\n")
                    .append("                            ")
                    .append(input.required())
                    .append(");\n\n");
        }
    }

    private void appendApplyMethod(
            StringBuilder source,
            MappingSpec mapping) {

        source.append("    @Override\n")
                .append("    public ")
                .append(mapping.outputType())
                .append(" applyTile(\n")
                .append("            beastconfig.BEASTState beastState,\n")
                .append("            java.util.IdentityHashMap<\n")
                .append("                    org.phylospec.ast.Expr.Variable,\n")
                .append("                    Integer> indexVariables) {\n\n");

        for (InputSpec input : mapping.inputs()) {
            source.append("        ")
                    .append(input.valueType())
                    .append(" ")
                    .append(valueName(input))
                    .append(" =\n")
                    .append("                this.")
                    .append(fieldName(input))
                    .append(".apply(\n")
                    .append("                        beastState,\n")
                    .append("                        indexVariables);\n\n");
        }

        source.append("        ")
                .append(mapping.implementationType())
                .append(" object =\n")
                .append("                new ")
                .append(mapping.implementationType())
                .append("();\n\n");

        for (InputSpec input : mapping.inputs()) {
            if (input.required()) {
                appendSetInput(
                        source,
                        input,
                        false);
            } else {
                appendSetInput(
                        source,
                        input,
                        true);
            }
        }

        source.append("        return object;\n")
                .append("    }\n");
    }

    private void appendSetInput(
            StringBuilder source,
            InputSpec input,
            boolean optional) {

        if (optional) {
            source.append("        if (")
                    .append(valueName(input))
                    .append(" != null) {\n")
                    .append("    ");
        }

        source.append("        beastState.setInput(\n")
                .append("                object,\n")
                .append("                object.")
                .append(input.input())
                .append(",\n")
                .append("                ")
                .append(valueName(input))
                .append(");\n\n");

        if (optional) {
            source.append("        }\n\n");
        }
    }

    private String fieldName(
            InputSpec input) {

        return input.declaration()
                .getSimpleName()
                .toString()
                + "Input";
    }

    private String valueName(
            InputSpec input) {

        return input.declaration()
                .getSimpleName()
                .toString()
                + "Value";
    }

    private String javaString(
            String value) {

        return "\""
                + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }
}