package org.phylospec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.converters.JSONConverter;
import org.phylospec.converters.JSONModel;
import org.phylospec.converters.LPhyConverter;
import org.phylospec.converters.RevConverter;
import org.phylospec.errors.Diagnostic;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.repository.PhyloSpecRepository;
import org.phylospec.workspace.Workspace;

/// This class provides a bunch of tools revolving about .phylospec files.
///
/// It can be called using the following CLI arguments:
/// - `validate [--json] [--engine name[@version]]... file.phylospec` - parses the file and runs
// the type checker, reporting every error and warning. With `--json`, prints a machine-readable
// report to stdout instead. Each `--engine` also warns about parts of the model that engine
// cannot run; it is looked up in the central repository, or read from a local engine
// specification if it is a path to a `.json` file.
// Exits with 0 if there are no errors (warnings are allowed), 1 if there are errors, and 2 if the
// command is used incorrectly or the file cannot be read.
/// - `to-lphy file.phylospec` - converts the given script into an LPhy script and prints it to
// stdout.
/// - `to-rev file.phylospec` - converts the given script into a Rev script and prints it to stdout.
/// - `to-json file.phylospec` - converts the given script into JSON and prints it to stdout.
/// - `json-schema` - prints the JSON schema of the JSON representation of PhyloSpec scripts to
// stdout.
public class Tools {
    public static void main(String[] args) throws IOException, JSONConverter.JsonConversionError {
        if (args.length == 0) {
            throw new RuntimeException("At least one argument with the tool name has to be provided.");
        }

        String toolName = args[0];
        switch (toolName) {
            case "validate" -> System.exit(validate(args, System.out, System.err));
            case "json-schema" -> printJsonSchema(args);
            case "to-json" -> convertToJson(args);
            case "to-rev" -> convertToRev(args);
            case "to-lphy" -> convertToLPhy(args);
            default -> throw new InputMismatchException("Unknown tool " + toolName);
        }
    }

    /**
     * Parses the file and runs the type checker, printing every diagnostic found.
     *
     * <p>The JSON report has the form:
     *
     * <pre>{@code
     * {
     *   "file": "model.phylospec",
     *   "engines": ["beast3@1.0.0"],
     *   "valid": false,
     *   "errorCount": 1,
     *   "warningCount": 0,
     *   "diagnostics": [
     *     {
     *       "severity": "error",
     *       "line": 3, "column": 5, "endLine": 3, "endColumn": 9,
     *       "message": "...",
     *       "hint": "...",
     *       "examples": ["..."]
     *     }
     *   ]
     * }
     * }</pre>
     *
     * Lines and columns are 1-based and {@code endColumn} is inclusive. The location
     * fields are null when an error has no known position. Unsupported generators are
     * reported as warnings for the engines passed with {@code --engine}.
     *
     * @return the process exit code: 0 if there are no errors, 1 if there are errors,
     *     and 2 if the arguments are invalid or the file cannot be read
     */
    static int validate(String[] args, PrintStream out, PrintStream err) {
        final String usage = "Usage: validate [--json] [--engine name[@version]]... file.phylospec";

        boolean json = false;
        String file = null;
        List<String> engineArguments = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--json")) {
                json = true;
            } else if (args[i].equals("--engine") && i + 1 < args.length) {
                engineArguments.add(args[++i]);
            } else if (file == null && !args[i].startsWith("--")) {
                file = args[i];
            } else {
                err.println(usage);
                return 2;
            }
        }
        if (file == null) {
            err.println(usage);
            return 2;
        }

        Path phylospecFile = Paths.get(file);
        String phylospecSource;
        List<ComponentLibrary> componentLibraries;
        try {
            phylospecSource = readPhyloSpecSource(phylospecFile);
            componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        } catch (IOException e) {
            err.println("Could not read " + file + ": " + e.getMessage());
            return 2;
        }

        Workspace workspace = new Workspace();
        Path folder = phylospecFile.toAbsolutePath().getParent();
        if (folder != null) workspace.FOLDERS.add(folder);

        // keep stdout clean for the JSON report, even if something prints while loading engines
        // or checking
        PrintStream originalOut = System.out;
        List<EngineSpecificationSchema> engines;
        List<Diagnostic> diagnostics;
        if (json) System.setOut(err);
        try {
            try {
                engines = loadEngines(engineArguments);
            } catch (IOException | IllegalArgumentException e) {
                err.println(e.getMessage());
                return 2;
            }

            Validator validator = new Validator(new ComponentResolver(componentLibraries), workspace, engines);
            diagnostics = validator.validate(phylospecSource).diagnostics();
        } finally {
            if (json) System.setOut(originalOut);
        }

        long errorCount = diagnostics.stream().filter(Diagnostic::isError).count();
        long warningCount = diagnostics.size() - errorCount;

        if (json) {
            out.println(toJsonReport(file, engines, diagnostics, errorCount, warningCount));
        } else {
            for (Diagnostic diagnostic : diagnostics) {
                out.print(diagnostic.error().toStdOutString(phylospecSource, !diagnostic.isError()));
            }
        }

        return errorCount == 0 ? 0 : 1;
    }

    /**
     * Resolves each {@code --engine} argument to an engine specification. An argument ending
     * in {@code .json} is read as a local specification file; anything else is a name with an
     * optional {@code @version}, looked up in the central repository.
     *
     * @throws IllegalArgumentException if an engine or version is not in the repository
     * @throws IOException              if a file or the repository cannot be read
     */
    private static List<EngineSpecificationSchema> loadEngines(List<String> engineArguments) throws IOException {
        List<EngineSpecificationSchema> engines = new ArrayList<>();
        PhyloSpecRepository repository = null;

        for (String argument : engineArguments) {
            if (argument.endsWith(".json")) {
                try {
                    engines.add(new ObjectMapper()
                            .readValue(Paths.get(argument).toFile(), EngineSpecificationSchema.class));
                } catch (IOException e) {
                    throw new IOException(
                            "Could not read the engine specification " + argument + ": " + e.getMessage(), e);
                }
                continue;
            }

            if (repository == null) {
                try {
                    repository = PhyloSpecRepository.loadCentral();
                } catch (IOException | RuntimeException e) {
                    throw new IOException(
                            "Could not load the PhyloSpec repository to look up engine " + argument + ": "
                                    + e.getMessage(),
                            e);
                }
            }

            int at = argument.indexOf('@');
            String name = at < 0 ? argument : argument.substring(0, at);
            String version = at < 0 ? null : argument.substring(at + 1);

            Optional<EngineSpecificationSchema> engine = version == null
                    ? repository.getLatestEngines().stream()
                            .filter(e -> e.getName().equals(name))
                            .findFirst()
                    : repository.getEngine(name, version);

            if (engine.isEmpty()) {
                List<String> available = repository.getLatestEngines().stream()
                        .map(EngineSpecificationSchema::getName)
                        .toList();
                String message = version == null
                        ? "Unknown engine " + name + ". Available engines: " + String.join(", ", available) + "."
                        : "Unknown version " + version + " of engine " + name + ". Available versions: "
                                + String.join(", ", repository.getAvailableEngineVersions(name)) + ".";
                throw new IllegalArgumentException(message);
            }
            engines.add(engine.get());
        }

        return engines;
    }

    private static String toJsonReport(
            String file,
            List<EngineSpecificationSchema> engines,
            List<Diagnostic> diagnostics,
            long errorCount,
            long warningCount) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode report = mapper.createObjectNode();
        report.put("file", file);
        ArrayNode engineNodes = report.putArray("engines");
        engines.forEach(engine -> engineNodes.add(engine.getName() + "@" + engine.getEngineVersion()));
        report.put("valid", errorCount == 0);
        report.put("errorCount", errorCount);
        report.put("warningCount", warningCount);

        ArrayNode diagnosticNodes = report.putArray("diagnostics");
        for (Diagnostic diagnostic : diagnostics) {
            ObjectNode node = diagnosticNodes.addObject();
            node.put("severity", diagnostic.isError() ? "error" : "warning");
            node.put("line", diagnostic.line());
            node.put("column", diagnostic.column());
            node.put("endLine", diagnostic.endLine());
            node.put("endColumn", diagnostic.endColumn());
            node.put("message", diagnostic.message());
            node.put("hint", diagnostic.hint());
            ArrayNode examples = node.putArray("examples");
            diagnostic.examples().forEach(examples::add);
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the validation report.", e);
        }
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

        String revString =
                RevConverter.convertToRev(pylospecFile.getFileName().toString(), statements, componentLibraries);
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
