package runner;

import beast.base.core.BEASTVersion2;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import org.phylospec.errors.Error;
import org.phylospec.tiling.errors.WrappedTileApplicationError;
import org.xml.sax.SAXException;

/** Command-line entry point for running a PhyloSpec source file with BEAST 3. */
public final class PhyloSpecCli {

    private static final String USAGE = """
            Usage: phylospec check [options] <model.phylospec>
                   phylospec run [options] <model.phylospec>
                   phylospec [options] <model.phylospec>  (legacy run)
                   phylospec --help | --version

            Commands:
              check  Check syntax, static types and BEAST 3 mappings; do not run MCMC.
                     A successful check does not validate all data or native initialization.
              run    Check and execute the model with BEAST 3.

            Options:
              --engine beast3        Engine (default: beast3; no other engine is supported yet)
              --library <id[,id...]>  Tile libraries in preference order
              --adapter-path <dir>   check/run: unpacked local adapter bundle (repeatable)
              --out <directory>      Run only: exact run directory (must be absent or empty)
              --run-name <prefix>    Run only: relative output prefix (default: script name)
              --debug                Include stack traces when checking or execution fails
              -h, --help              Show this help
              --version              Show PhyloSpec and loaded BEAST runtime versions
              --                     Treat remaining arguments as file names

            check/run default to the base library 'beast2' (its existing internal ID).
            When libraries are specified, 'beast2' is appended only if absent.
            Adapter paths use the command's working directory and contain direct *.jar files.
            Use --library to select an adapter; supplying its directory does not select it.
            The host PhyloSpec/BEAST runtime is retained; bundle copies do not replace it.
            Bundles are local executable Java code. No packages are downloaded or installed.
            Legacy run requires --library and preserves its exact library order.
            New run writes to --out/output/, or beside the script in runs/<name>-<run-id>/output/.
            Relative --out paths use the command's working directory; input paths use the script directory.
            Script outputPrefix overrides --run-name; explicit logger names are relative to output/.
            Output paths must stay inside output/. Absolute script output paths are rejected.
            Managed runs save model.phylospec, run.json and console.log beside output/.
            A nonterminal or unconfirmed run.json does not confirm that a process is still running.
            Legacy run retains its existing --run-name path and script output behavior; --out is unavailable.
            """;
    private static final String BASE_LIBRARY = "beast2";

    private PhyloSpecCli() {}

    public static void main(String[] arguments) {
        int exitCode = run(arguments, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    /** Keeps command errors separate from failures after a valid command has been accepted. */
    static int run(String[] arguments, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(arguments);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.print(USAGE);
            return 2;
        }

        try {
            execute(options, out, err);
            return 0;
        } catch (PhyloSpecException e) {
            err.println(displayDiagnostic(e).format());
            if (options.debug()) e.printStackTrace(err);
        } catch (CancellationException e) {
            err.println("Run cancelled: " + failureMessage(e));
            if (options.debug()) e.printStackTrace(err);
            return 130;
        } catch (IOException | ParserConfigurationException | SAXException | RuntimeException e) {
            err.println("Could not " + options.action() + ": " + failureMessage(e));
            if (options.debug()) e.printStackTrace(err);
        } catch (LinkageError | ServiceConfigurationError e) {
            err.println("Could not load a required runtime dependency: " + failureMessage(e));
            if (options.debug()) e.printStackTrace(err);
        }
        return 1;
    }

    /** Executes a command without terminating the calling JVM. */
    public static void execute(String[] arguments) throws IOException, ParserConfigurationException, SAXException {
        execute(Options.parse(arguments), System.out, System.err);
    }

    private static void execute(Options options, PrintStream out, PrintStream err)
            throws IOException, ParserConfigurationException, SAXException {
        if (options.help()) {
            out.print(USAGE);
            return;
        }
        if (options.version()) {
            out.println("PhyloSpec BEAST 3 integration " + phyloSpecVersion());
            out.println("BEAST runtime " + BEASTVersion2.INSTANCE.getVersionString());
            return;
        }

        Path source = options.source().toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IOException("PhyloSpec source file not found or not a regular file: " + source);
        }

        try (LocalAdapterBundles adapters = LocalAdapterBundles.open(options.adapterPaths())) {
            executeModel(options, source, adapters, out, err);
        }
    }

    private static void executeModel(
            Options options, Path source, LocalAdapterBundles adapters, PrintStream out, PrintStream err)
            throws IOException, ParserConfigurationException, SAXException {
        PhyloSpecRunner runner = new PhyloSpecRunner(source, options.libraries(), adapters.bundles());
        Consumer<PhyloSpecDiagnostic> warningHandler = warning -> err.println(warning.format());
        if (options.command() == Command.CHECK) {
            try {
                runner.check().warnings().forEach(warningHandler);
            } catch (PhyloSpecException e) {
                e.warnings().forEach(warningHandler);
                throw e;
            }
            out.println("Check passed (engine: beast3; libraries: " + String.join(",", options.libraries())
                    + "). No MCMC was run.");
            return;
        }

        if (options.command() == Command.RUN) {
            String scriptName = withoutExtension(source).getFileName().toString();
            Path runDirectory = options.outputDirectory() == null
                    ? source.getParent().resolve("runs").resolve(scriptName + "-" + UUID.randomUUID())
                    : options.outputDirectory().toAbsolutePath().normalize();
            String runName = options.runName() == null ? scriptName : options.runName();
            try {
                runner.runManaged(runName, runDirectory, warningHandler);
            } finally {
                if (Files.isRegularFile(runDirectory.resolve("run.json"))) {
                    out.println("Results directory: " + runDirectory);
                }
            }
            out.println("Run completed (engine: beast3; libraries: " + String.join(",", options.libraries()) + ").");
            return;
        }

        String runName = options.runName() == null
                ? withoutExtension(source).toString()
                : Path.of(options.runName()).toAbsolutePath().normalize().toString();
        runner.runPhyloSpec(runName, warningHandler);
    }

    static String phyloSpecVersion() throws IOException {
        String version = PhyloSpecCli.class.getPackage().getImplementationVersion();
        if (version != null && !version.isBlank()) return version;

        try (InputStream input =
                PhyloSpecCli.class.getResourceAsStream("/META-INF/phylospec/beast3-build.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                version = properties.getProperty("phylospec.version");
                if (version != null && !version.isBlank() && !version.contains("${")) return version;
            }
        }
        return "development (version unavailable)";
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static PhyloSpecDiagnostic displayDiagnostic(PhyloSpecException failure) {
        PhyloSpecDiagnostic diagnostic = failure.diagnostic();
        if (!(failure.getCause() instanceof WrappedTileApplicationError wrapped)) return diagnostic;

        // The core wrapper embeds a native stack trace in its hint. Keep that detail for --debug.
        Error detail = diagnostic.error();
        Error concise = new Error(
                detail.range(),
                detail.description(),
                "BEAST: " + failureMessage(wrapped.getEngineException()),
                detail.examples());
        return new PhyloSpecDiagnostic(
                diagnostic.stage(), concise, diagnostic.warning(), diagnostic.source(), diagnostic.sourceFile());
    }

    private static Path withoutExtension(Path source) {
        String name = source.getFileName().toString();
        String suffix = ".phylospec";
        if (name.endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length());
        }
        return source.resolveSibling(name);
    }

    enum Command {
        CHECK,
        RUN,
        LEGACY_RUN
    }

    record Options(
            Command command,
            Path source,
            String runName,
            Path outputDirectory,
            List<String> libraries,
            List<Path> adapterPaths,
            boolean help,
            boolean version,
            boolean debug) {

        static Options parse(String[] arguments) {
            Command command = Command.LEGACY_RUN;
            int firstOption = 0;
            if (arguments.length > 0) {
                if (arguments[0].equals("check")) {
                    command = Command.CHECK;
                    firstOption = 1;
                } else if (arguments[0].equals("run")) {
                    command = Command.RUN;
                    firstOption = 1;
                }
            }
            Path source = null;
            String runName = null;
            Path outputDirectory = null;
            List<String> libraries = new ArrayList<>();
            List<Path> adapterPaths = new ArrayList<>();
            boolean help = false;
            boolean version = false;
            boolean debug = false;
            boolean fileNamesOnly = false;

            for (int index = firstOption; index < arguments.length; index++) {
                String argument = arguments[index];
                if (fileNamesOnly) {
                    if (source != null)
                        throw new IllegalArgumentException("Only one PhyloSpec source file may be supplied.");
                    source = Path.of(argument);
                    continue;
                }
                switch (argument) {
                    case "-h", "--help" -> help = true;
                    case "--version" -> version = true;
                    case "--debug" -> debug = true;
                    case "--" -> fileNamesOnly = true;
                    case "--engine" -> {
                        String engine = requireValue(arguments, ++index, "--engine");
                        if (!engine.equals("beast3")) {
                            throw new IllegalArgumentException(
                                    "Unsupported engine '" + engine + "'. Only beast3 is supported.");
                        }
                    }
                    case "--library" -> {
                        String value = requireValue(arguments, ++index, "--library");
                        for (String part : value.split(",", -1)) {
                            String id = part.trim();
                            if (id.isBlank()) {
                                throw new IllegalArgumentException("Tile library identifiers must not be blank.");
                            }
                            if (libraries.contains(id)) {
                                throw new IllegalArgumentException(
                                        "Tile library '" + id + "' was selected more than once.");
                            }
                            libraries.add(id);
                        }
                    }
                    case "--run-name" -> {
                        runName = requireValue(arguments, ++index, "--run-name");
                        if (runName.isBlank())
                            throw new IllegalArgumentException("--run-name requires a non-blank path.");
                        Path.of(runName); // Report malformed paths as command errors, before execution.
                    }
                    case "--adapter-path" -> {
                        String value = requireValue(arguments, ++index, "--adapter-path");
                        if (value.isBlank())
                            throw new IllegalArgumentException("--adapter-path requires a non-blank directory.");
                        adapterPaths.add(Path.of(value));
                    }
                    case "--out" -> {
                        String value = requireValue(arguments, ++index, "--out");
                        if (value.isBlank())
                            throw new IllegalArgumentException("--out requires a non-blank directory.");
                        outputDirectory = Path.of(value);
                    }
                    default -> {
                        if (argument.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + argument);
                        }
                        if (source != null) {
                            throw new IllegalArgumentException("Only one PhyloSpec source file may be supplied.");
                        }
                        source = Path.of(argument);
                    }
                }
            }

            if (!help && !version) {
                if (source == null) throw new IllegalArgumentException("A PhyloSpec source file is required.");
                if (command == Command.CHECK && runName != null) {
                    throw new IllegalArgumentException("--run-name is only supported for run.");
                }
                if (command != Command.RUN && outputDirectory != null) {
                    throw new IllegalArgumentException("--out is only supported for the run subcommand.");
                }
                if (command == Command.LEGACY_RUN && !adapterPaths.isEmpty()) {
                    throw new IllegalArgumentException("--adapter-path is only supported for check/run subcommands.");
                }
                if (command == Command.LEGACY_RUN && libraries.isEmpty()) {
                    throw new IllegalArgumentException("At least one --library must be supplied.");
                }
            }
            if (command != Command.LEGACY_RUN && !libraries.contains(BASE_LIBRARY)) {
                libraries.add(BASE_LIBRARY);
            }
            return new Options(
                    command,
                    source,
                    runName,
                    outputDirectory,
                    List.copyOf(libraries),
                    List.copyOf(adapterPaths),
                    help,
                    version,
                    debug);
        }

        String action() {
            if (version) return "read version information";
            return command == Command.CHECK ? "check the PhyloSpec model" : "run the PhyloSpec model";
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].startsWith("-")) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return arguments[index];
        }
    }
}
