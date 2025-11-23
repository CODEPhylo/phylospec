package org.phylospec;

import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.converters.JSONConverter;
import org.phylospec.converters.JSONModel;
import org.phylospec.converters.LPhyConverter;
import org.phylospec.converters.RevConverter;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.InputMismatchException;
import java.util.List;

/// This class provides a bunch of tools revolving about .phylospec files.
///
/// It can be called using the following CLI arguments:
/// - `validate file.phylospec` - tries to parse the file and runs the type checker.
/// - `to-lphy file.phylospec` - converts the given script into an LPhy script and prints it to stdout.
/// - `to-rev file.phylospec` - converts the given script into a Rev script and prints it to stdout.
/// - `to-json file.phylospec` - converts the given script into JSON and prints it to stdout.
/// - `json-schema` - prints the JSON schema of the JSON representation of PhyloSpec scripts to stdout.
public class Tools {
    public static void main(String[] args) throws IOException, JSONConverter.JsonConversionError {
        if (args.length == 0) {
            throw new RuntimeException("At least one argument with the tool name has to be provided.");
        }

        String toolName = args[0];
        switch (toolName) {
            case "validate" -> validate(args);
            case "json-schema" -> printJsonSchema(args);
            case "to-json" -> convertToJson(args);
            case "to-rev" -> convertToRev(args);
            case "to-lphy" -> convertToLPhy(args);
            default -> throw new InputMismatchException("Unknown tool " + toolName);
        }
    }

    /** Parses the file and runs the type resolver to detect type errors. */
    private static void validate(String[] args) throws IOException {
        if (args.length != 2) {
            throw new RuntimeException("validate requires you to pass a path to a  phylospec file.");
        }

        Path pylospecFile = Paths.get(args[1]);
        String phylospecSource = readPhyloSpecSource(pylospecFile);
        List<Stmt> statements = parseStmts(phylospecSource);

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        ComponentResolver componentResolver = new ComponentResolver(componentLibraries);
        TypeResolver typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);
    }

    /** Prints out the JSON schema for the output of the to-json command. */
    private static void printJsonSchema(String[] args) {
        if (args.length != 1) {
            throw new RuntimeException("json-schema requires no arguments.");
        }
        System.out.println(JSONModel.getJSONSchema());
    }

    /** Parses the file, runs the type resolver, and prints a JSON representation of it to stdout. */
    private static void convertToJson(String[] args) throws IOException, JSONConverter.JsonConversionError {
        if (args.length != 2) {
            throw new RuntimeException("to-json requires you to pass a path to a  phylospec file.");
        }

        Path pylospecFile = Paths.get(args[1]);
        String phylospecSource = readPhyloSpecSource(pylospecFile);
        List<Stmt> statements = parseStmts(phylospecSource);

        String jsonString = JSONConverter.convertToJSON(statements, phylospecSource);
        System.out.println(jsonString);
    }

    /** Parses the file, runs the type resolver, and prints the corresponding Rev script to stdout. */
    private static void convertToRev(String[] args) throws IOException {
        if (args.length != 2) {
            throw new RuntimeException("to-rev requires you to pass a path to a  phylospec file.");
        }

        Path pylospecFile = Paths.get(args[1]);
        String phylospecSource = readPhyloSpecSource(pylospecFile);
        List<Stmt> statements = parseStmts(phylospecSource);

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();

        String revString = RevConverter.convertToRev(pylospecFile.getFileName().toString(), statements, componentLibraries);
        System.out.println(revString);
    }

    /** Parses the file, runs the type resolver, and prints the corresponding LPhy script to stdout. */
    private static void convertToLPhy(String[] args) throws IOException {
        if (args.length != 2) {
            throw new RuntimeException("to-lphy requires you to pass a path to a  phylospec file.");
        }

        Path pylospecFile = Paths.get(args[1]);
        String phylospecSource = readPhyloSpecSource(pylospecFile);
        List<Stmt> statements = parseStmts(phylospecSource);

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();

        String lphyString = LPhyConverter.convertToLPhy(statements, componentLibraries);
        System.out.println(lphyString);
    }

    private static List<Stmt> parseStmts(String phylospecSource) {
        Lexer lexer = new Lexer(phylospecSource);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    private static String readPhyloSpecSource(Path pylospecFile) throws IOException {
        byte[] phylospecBytes = Files.readAllBytes(pylospecFile);
        return new String(phylospecBytes, Charset.defaultCharset());
    }

}
