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
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import org.phylospec.errors.Error;
import org.phylospec.tiling.errors.WrappedTileApplicationError;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.xml.sax.SAXException;

/** Command-line entry point for checking scripts, exporting analysis XML and running managed analyses. */
public final class PhyloSpecCli {

    private static final String USAGE = """
            Usage: phylospec check [options] <model.phylospec>
                   phylospec export [options] <model.phylospec> --xml <analysis.xml>
                   phylospec run [options] <model.phylospec>
                   phylospec [options] <model.phylospec>  (legacy run)
                   phylospec --help | --version

            Commands:
              check  Check syntax, static types and mappings for the selected engine; do not run MCMC.
                     A successful check does not validate all data or native initialization.
              export Write and load-check analysis XML for the selected engine without running MCMC.
              run    Check and execute the model with the selected engine.

            Options:
              --engine <id>          check/export/run: beast3 (default) or beastx
              --library <id[,id...]>  Tile libraries in preference order
              --adapter-path <dir>   check/export/run: unpacked local adapter bundle (repeatable)
              --xml <file>           Export only: destination XML (default log prefix: XML file stem)
              --run-name <name>      Run only: name of the output files (default: script name)
              --seed <n>             export/run: random seed (default: chosen at random)
              --overwrite            Run only: replace existing output files and the run record
              --debug                Show raw BEAST console output and failure stack traces
              -h, --help              Show this help
              --version              Show PhyloSpec and the default BEAST 3 runtime versions
              --                     Treat remaining arguments as file names

            BEAST X check/export/run uses its base library 'beastX' and does not load local adapters.
            BEAST 3 check/export/run default to the base library 'beast2' (its existing internal ID).
            When libraries are specified, 'beast2' is appended only if absent.
            Adapter paths use the command's working directory and contain direct *.jar files.
            Use --library to select an adapter; supplying its directory does not select it.
            The host PhyloSpec/BEAST runtime is retained; bundle copies do not replace it.
            Bundles are local executable Java code. No packages are downloaded or installed.
            Legacy run requires --library and preserves its exact library order.
            Logs, trees and state files are written to the working directory. Input paths use the script directory.
            Existing outputs are never replaced without --overwrite.
            The run record (run.json, console.log, model.phylospec) is saved next to them in <name>.run/.
            BEAST X also saves the executed analysis.xml in the run record.
            A nonterminal or unconfirmed run.json does not confirm that a process is still running.
            Legacy run retains its existing --run-name path and script output behavior.
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
            return execute(options, out, err);
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
        Options options = Options.parse(arguments);
        if (execute(options, System.out, System.err) != 0) {
            throw new IOException("Could not " + options.action() + ". See the diagnostic above.");
        }
    }

    private static int execute(Options options, PrintStream out, PrintStream err)
            throws IOException, ParserConfigurationException, SAXException {
        if (options.help()) {
            out.print(USAGE);
            return 0;
        }
        if (options.version()) {
            out.println("PhyloSpec BEAST 3 integration " + phyloSpecVersion());
            out.println("BEAST runtime " + BEASTVersion2.INSTANCE.getVersionString());
            return 0;
        }

        Path source = options.source().toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IOException("PhyloSpec source file not found or not a regular file: " + source);
        }

        if (options.engine().equals("beastx")) {
            if (options.command() == Command.RUN) {
                return InstalledBeastXRun.run(options, source, out, err);
            }
            return InstalledEngineTool.run(options.command(), options, source, out, err);
        }
        try (LocalAdapterBundles adapters = LocalAdapterBundles.open(options.adapterPaths())) {
            executeModel(options, source, adapters, out, err);
        }
        return 0;
    }

    private static void executeModel(
            Options options, Path source, LocalAdapterBundles adapters, PrintStream out, PrintStream err)
            throws IOException, ParserConfigurationException, SAXException {
        PhyloSpecRunner runner = new PhyloSpecRunner(source, options.libraries(), adapters.bundles());
        runner.setSeed(options.seed());
        runner.setOverwrite(options.overwrite());
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
            out.println("Checked: syntax, static types and available BEAST 3 mappings.");
            out.println("Not checked: complete input-data validity, native model initialization or MCMC convergence.");
            out.println("Next: use 'phylospec run' with the same script and library/adapter options.");
            return;
        }

        if (options.command() == Command.EXPORT) {
            runner.exportXml(options.xmlFile(), warningHandler);
            out.println("Exported BEAST 3 analysis: "
                    + options.xmlFile().toAbsolutePath().normalize());
            return;
        }

        if (options.command() == Command.RUN) {
            Path runDirectory = options.runDirectory(source);
            String runName = options.outputPrefix(source);
            RunRecord record;
            try {
                record = runner.runManaged(runName, runDirectory, warningHandler, out::println, options.debug());
            } finally {
                if (Files.isRegularFile(runDirectory.resolve("run.json"))) {
                    out.println("Run record: " + runDirectory.resolve("run.json"));
                    if (Files.isRegularFile(runDirectory.resolve("console.log"))) {
                        out.println("BEAST console log: " + runDirectory.resolve("console.log"));
                    }
                }
            }
            out.println("Run completed (engine: beast3; libraries: " + String.join(",", options.libraries()) + ").");
            for (var file : record.outputFiles()) {
                String label =
                        switch (file.kind()) {
                            case "tree" -> "Trees";
                            case "state" -> "Final state";
                            default -> "Trace/log";
                        };
                out.println(label + ": " + file.path());
            }
            out.println("Execution and output checks succeeded; MCMC convergence has not been assessed.");
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
        EXPORT,
        RUN,
        LEGACY_RUN
    }

    record Options(
            Command command,
            String engine,
            Path source,
            String runName,
            Path xmlFile,
            Long seed,
            List<String> libraries,
            List<Path> adapterPaths,
            boolean overwrite,
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
                } else if (arguments[0].equals("export")) {
                    command = Command.EXPORT;
                    firstOption = 1;
                } else if (arguments[0].equals("run")) {
                    command = Command.RUN;
                    firstOption = 1;
                }
            }
            String selectedEngine = null;
            Path source = null;
            String runName = null;
            Path xmlFile = null;
            Long seed = null;
            List<String> libraries = new ArrayList<>();
            List<Path> adapterPaths = new ArrayList<>();
            boolean help = false;
            boolean version = false;
            boolean debug = false;
            boolean overwrite = false;
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
                    case "--overwrite" -> overwrite = true;
                    case "--" -> fileNamesOnly = true;
                    case "--engine" -> {
                        String engine = requireValue(arguments, ++index, "--engine");
                        if (!engine.equals("beast3") && !engine.equals("beastx")) {
                            throw new IllegalArgumentException(
                                    "Unsupported engine '" + engine + "'. Choose beast3 or beastx.");
                        }
                        if (selectedEngine != null && !selectedEngine.equals(engine)) {
                            throw new IllegalArgumentException("Select a single --engine per command.");
                        }
                        selectedEngine = engine;
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
                    case "--xml" -> {
                        String value = requireValue(arguments, ++index, "--xml");
                        if (value.isBlank()) throw new IllegalArgumentException("--xml requires a non-blank file.");
                        xmlFile = Path.of(value);
                    }
                    case "--seed" -> seed = parseSeed(requireValue(arguments, ++index, "--seed"));
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

            String engine = selectedEngine == null ? "beast3" : selectedEngine;
            if (!help && !version) {
                if (engine.equals("beastx")) {
                    if (command == Command.LEGACY_RUN) {
                        throw new IllegalArgumentException("Use the run subcommand for BEAST X.");
                    }
                    if (!adapterPaths.isEmpty() || !libraries.isEmpty() && !libraries.equals(List.of("beastX"))) {
                        throw new IllegalArgumentException(
                                "BEAST X supports only the base library beastX and no --adapter-path.");
                    }
                }
                if (source == null) throw new IllegalArgumentException("A PhyloSpec source file is required.");
                if ((command == Command.CHECK || command == Command.EXPORT) && runName != null) {
                    throw new IllegalArgumentException("--run-name is only supported for run.");
                }
                if (command == Command.EXPORT && xmlFile == null) {
                    throw new IllegalArgumentException("The export subcommand requires --xml <file>.");
                }
                if (command == Command.RUN && runName != null) {
                    OutputPaths.validateRunName(runName);
                }
                if (command != Command.EXPORT && xmlFile != null) {
                    throw new IllegalArgumentException("--xml is only supported for the export subcommand.");
                }
                if (command != Command.RUN && overwrite) {
                    throw new IllegalArgumentException("--overwrite is only supported for run.");
                }
                if (command == Command.CHECK && seed != null) {
                    throw new IllegalArgumentException("--seed is only supported for export and run.");
                }
                if (command == Command.LEGACY_RUN && !adapterPaths.isEmpty()) {
                    throw new IllegalArgumentException("--adapter-path is only supported for check/export/run.");
                }
                if (command == Command.LEGACY_RUN && libraries.isEmpty()) {
                    throw new IllegalArgumentException("At least one --library must be supplied.");
                }
            }
            String baseLibrary = engine.equals("beastx") ? "beastX" : BASE_LIBRARY;
            if (command != Command.LEGACY_RUN && !libraries.contains(baseLibrary)) {
                libraries.add(baseLibrary);
            }
            return new Options(
                    command,
                    engine,
                    source,
                    runName,
                    xmlFile,
                    seed,
                    List.copyOf(libraries),
                    List.copyOf(adapterPaths),
                    overwrite,
                    help,
                    version,
                    debug);
        }

        Path runDirectory(Path source) {
            return new OutputPaths(Path.of("")).getRecordDirectory(this.outputPrefix(source));
        }

        String outputPrefix(Path source) {
            return runName == null ? withoutExtension(source).getFileName().toString() : runName;
        }

        String action() {
            if (version) return "read version information";
            return switch (command) {
                case CHECK -> "check the PhyloSpec model";
                case EXPORT -> "export the analysis XML";
                default -> "run the PhyloSpec model";
            };
        }

        private static long parseSeed(String value) {
            try {
                long seed = Long.parseLong(value);
                if (seed >= 0) return seed;
            } catch (NumberFormatException malformed) {
                // reported below together with negative seeds
            }
            throw new IllegalArgumentException("--seed requires a non-negative integer.");
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].startsWith("-")) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return arguments[index];
        }
    }
}
