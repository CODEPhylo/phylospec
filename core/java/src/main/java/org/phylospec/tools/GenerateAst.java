package org.phylospec.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];

        defineAst(outputDir, "Expr", Arrays.asList(
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Unary    : Token operator, Expr right",
                "Binary   : Expr left, Token operator, Expr right"
        ));
    }

    private static void defineAst(
            String outputDir, String baseName, List<String> types)
            throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package org.phylospec.ast;");
        writer.println();
        writer.println("import org.phylospec.lexer.Token;");
        writer.println();
        writer.println("abstract class " + baseName + " {");
        writer.println();

        // the AST classes
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
            writer.println();
        }

        writer.println("}");
        writer.close();
    }

    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        writer.println("\tstatic class " + className + " extends " +
                baseName + " {");

        // constructor.
        writer.println("\t\t" + className + "(" + fieldList + ") {");

        // store parameters in fields
        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("\t\t\tthis." + name + " = " + name + ";");
        }

        writer.println("\t\t}");

        // fields
        writer.println();
        for (String field : fields) {
            writer.println("\t\tfinal " + field + ";");
        }

        writer.println("\t}");
    }
}