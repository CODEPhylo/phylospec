package runner;

import beast.base.core.BEASTVersion2;
import beast.base.inference.*;
import beast.base.parser.XMLParser;
import beast.base.parser.XMLParserException;
import beast.base.parser.XMLProducer;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import beastconfig.ReportingTreeLikelihood;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.phylospec.tiling.mcmc.RunRecord;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.workspace.Workspace;
import org.xml.sax.SAXException;

/// Top-level entry point for running a PhyloSpec model against BEAST 3.
///
/// Orchestrates the full pipeline: lexing and parsing the source, simplifying the AST,
/// resolving variables and types, applying tiles to build a BEAST object graph, assembling
/// the MCMC run (state, distributions, operators, loggers), and finally executing it.
public class PhyloSpecRunner {

    private final String source;
    private final Optional<List<String>> tileLibraryIds;
    private final Path sourceDirectory;
    private final Path sourceFile;
    private final List<LocalAdapterBundles.Bundle> adapterBundles;
    private Long seed;
    private boolean overwrite;

    /**
     * Constructs a runner for the given PhyloSpec source code.
     */
    public PhyloSpecRunner(String source) {
        this.source = source;
        this.tileLibraryIds = Optional.empty();
        this.sourceDirectory = Path.of("").toAbsolutePath().normalize();
        this.sourceFile = null;
        this.adapterBundles = List.of();
    }

    /**
     * Constructs a runner using only the named Tile libraries. Library identifiers are ordered
     * from most to least preferred, so a selected package can override an equivalent base mapping.
     */
    public PhyloSpecRunner(String source, List<String> tileLibraryIds) {
        this.source = source;
        this.tileLibraryIds = Optional.of(List.copyOf(tileLibraryIds));
        this.sourceDirectory = Path.of("").toAbsolutePath().normalize();
        this.sourceFile = null;
        this.adapterBundles = List.of();
    }

    /** Constructs a runner from a source file, resolving relative model inputs beside that file. */
    public PhyloSpecRunner(Path sourceFile, List<String> tileLibraryIds) throws IOException {
        this(sourceFile, tileLibraryIds, List.of());
    }

    PhyloSpecRunner(Path sourceFile, List<String> tileLibraryIds, List<LocalAdapterBundles.Bundle> adapterBundles)
            throws IOException {
        Path absoluteSource = sourceFile.toAbsolutePath().normalize();
        this.source = Files.readString(absoluteSource, StandardCharsets.UTF_8);
        this.tileLibraryIds = Optional.of(List.copyOf(tileLibraryIds));
        this.sourceDirectory = absoluteSource.getParent();
        this.sourceFile = absoluteSource;
        this.adapterBundles = List.copyOf(adapterBundles);
    }

    /**
     * Sets the random seed used to build and run the model. Without a seed, managed runs choose
     * one and all other runs leave the random number generator as it is.
     */
    public void setSeed(Long seed) {
        this.seed = seed;
    }

    /** Sets whether managed runs replace existing output files and run records. */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * Checks syntax, static types and available BEAST mappings without applying tiles or running
     * MCMC. This does not validate all data or native initialization. Adapter registration and
     * existing type-property inference still take place.
     *
     * @throws PhyloSpecException if a source or mapping error is detected
     */
    public CheckedModel check() throws IOException {
        return check(warning -> {});
    }

    private CheckedModel check(Consumer<PhyloSpecDiagnostic> warningHandler) throws IOException {
        TileCatalog<BEASTState> catalog = tileLibraryIds.isPresent()
                ? TileCatalog.select(BEASTState.class, tileLibraryIds.get())
                : TileCatalog.discover(BEASTState.class);
        Diagnostics diagnostics = new Diagnostics(warningHandler);
        catalog.getComponentResolver()
                .setUnknownNamespaceHint(namespace -> "No loaded component library provides '" + namespace
                        + "'. If an adapter bundle provides it, pass the bundle directory with --adapter-path"
                        + " and select its library with --library (e.g. --adapter-path <bundle> --library "
                        + namespace + ").");

        // run lexer

        Lexer lexer = new Lexer(this.source);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.LEXING;
        lexer.registerEventListener(diagnostics);
        List<Token> tokens = lexer.scanTokens();

        // run parser

        Parser parser = new Parser(tokens);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.PARSING;
        parser.registerEventListener(diagnostics);
        List<Stmt> statements = parser.parse();

        // simplify graph

        diagnostics.stage = PhyloSpecDiagnostic.Stage.SIMPLIFICATION;
        try {
            statements = new RemoveGroupings().transform(statements);
            statements = new EvaluateLiterals().transform(statements);
            statements = new EvaluateScalarFunctions().transform(statements);
        } catch (ArithmeticException | ClassCastException error) {
            throw diagnostics.failure(
                    new Error(
                            null,
                            "Could not evaluate a constant expression.",
                            "Check the operand types and values in constant expressions."),
                    error);
        }

        // run variable resolver

        VariableResolver variableResolver = new VariableResolver(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(catalog.getComponentResolver(), new Workspace(sourceDirectory));
        diagnostics.stage = PhyloSpecDiagnostic.Stage.TYPE_CHECKING;
        typeResolver.registerEventListener(diagnostics);

        try {
            typeResolver.visitStatements(statements);
        } catch (TypeError error) {
            throw diagnostics.failure(error.toError(sourceRange(parser, error.getAstNode())), error);
        }

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        // perform tiling

        EvaluateTiles<BEASTState> applyTiles =
                new EvaluateTiles<>(catalog.getTiles(), variableResolver, stochasticityResolver);
        diagnostics.stage = PhyloSpecDiagnostic.Stage.MAPPING;
        try {
            applyTiles.getBestTiling(statements);
        } catch (TileApplicationError error) {
            throw diagnostics.failure(error.toError(sourceRange(parser, error.getAstNode())), error);
        }

        return new CheckedModel(statements, parser, catalog, applyTiles, diagnostics.warnings);
    }

    /**
     * Runs the full pipeline using the same checks as {@link #check()}. Source failures are thrown
     * to the caller; use the overload with a warning handler to receive nonfatal diagnostics.
     */
    public void runPhyloSpec(String runName) throws IOException, ParserConfigurationException, SAXException {
        runPhyloSpec(runName, warning -> {});
    }

    /** Runs the model, delivering warnings without choosing an output stream for the caller. */
    public void runPhyloSpec(String runName, Consumer<PhyloSpecDiagnostic> warningHandler)
            throws IOException, ParserConfigurationException, SAXException {
        CheckedModel checked = check(warningHandler);
        runChecked(checked, new BEASTState(runName, sourceDirectory), null, () -> false, message -> {});
    }

    /** Exports a full analysis XML and verifies that BEAST can load the MCMC without running it. */
    public void exportXml(Path output) throws IOException, ParserConfigurationException, SAXException {
        exportXml(output, warning -> {});
    }

    void exportXml(Path output, Consumer<PhyloSpecDiagnostic> warningHandler)
            throws IOException, ParserConfigurationException, SAXException {
        Path target = output.toAbsolutePath().normalize();
        if (target.getFileName() == null || Files.isDirectory(target)) {
            throw new IOException("Expected an XML output file: " + target);
        }
        String name = target.getFileName().toString();
        String prefix = name.endsWith(".xml") && name.length() > 4 ? name.substring(0, name.length() - 4) : name;
        BEASTState state = BEASTState.forXmlExport(prefix, sourceDirectory);
        List<Path> inputs = new ArrayList<>();
        if (sourceFile != null) inputs.add(sourceFile);
        state.setInputObserver((reference, path) -> inputs.add(path));
        CheckedModel checked = check(warningHandler);
        Randomizer.getSeed();
        if (this.seed != null) Randomizer.setSeed(this.seed);
        state.randomSeed = this.seed;

        MCMC mcmc = new MCMC();
        buildMcmc(checked, state, mcmc);
        if (Files.exists(target)) {
            for (Path input : inputs) {
                if (Files.isSameFile(target, input)) throw new IOException("XML output is a model input: " + input);
            }
        }
        if (state.randomSeed != null) Randomizer.setSeed(state.randomSeed);
        state.initializeBEASTObjects();
        if (state.prepareXml != null) state.prepareXml.run();
        String xml = new XMLProducer().toXML(mcmc);
        if (xml == null || xml.isBlank()) throw new IOException("BEAST could not serialize the analysis XML.");

        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), ".phylospec-export-", ".xml");
        try {
            Files.writeString(staged, xml, StandardCharsets.UTF_8);
            if (!(new XMLParser().parseFile(staged.toFile()) instanceof MCMC)) {
                throw new IOException("The exported XML did not load as a BEAST MCMC.");
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (XMLParserException invalid) {
            throw new IOException("BEAST could not load the exported analysis: " + invalid.getMessage(), invalid);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /** Runs with isolated output paths; the original source directory remains the input base. */
    public void runManaged(String runName, Path runDirectory, Consumer<PhyloSpecDiagnostic> warningHandler)
            throws IOException, ParserConfigurationException, SAXException {
        runManaged(runName, runDirectory, warningHandler, message -> {}, true);
    }

    /** CLI feedback is optional; existing Java callers retain their original console behavior. */
    RunRecord runManaged(
            String runName,
            Path runDirectory,
            Consumer<PhyloSpecDiagnostic> warningHandler,
            Consumer<String> progress,
            boolean rawConsole)
            throws IOException, ParserConfigurationException, SAXException {
        String checking = "[1/4] Checking syntax, static types and BEAST 3 mappings...";
        progress.accept(checking);
        CheckedModel checked = check(warningHandler);
        OutputPaths.checkRuntimeOverrides();
        Path outputDirectory = Path.of("").toAbsolutePath();
        // reject an invalid prefix before claiming a directory
        new OutputPaths(outputDirectory).resolve(runName);
        OutputPaths.claimRecordDirectory(runDirectory, this.overwrite);
        RunRecord record =
                createRunRecord(runDirectory, source, sourceFile, sourceDirectory, checked.catalog, adapterBundles);
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        Thread owner = Thread.currentThread();
        Thread shutdown = new Thread(
                () -> {
                    // Request cooperative cancellation. Interrupting native file writes could truncate logs.
                    cancellationRequested.set(true);
                    try {
                        owner.join(2000);
                        record.markUnconfirmed("The JVM shut down before the runner confirmed a terminal outcome.");
                    } catch (IOException | InterruptedException error) {
                        System.err.println("Could not finalize run record during shutdown: " + error.getMessage());
                    }
                },
                "phylospec-run-shutdown");
        boolean hookRegistered = false;
        boolean restoreInterrupt = false;
        try {
            Runtime.getRuntime().addShutdownHook(shutdown);
            hookRegistered = true;
            try (RunConsole console = RunConsole.open(runDirectory.resolve("console.log"), rawConsole)) {
                console.log().println(checking);
                Consumer<String> report = message -> {
                    console.log().println(message);
                    progress.accept(message);
                };
                for (PhyloSpecDiagnostic warning : checked.warnings) record.addWarning(warning.format());
                checked.warnings.forEach(warning -> console.log().println(warning.format()));
                BEASTState state =
                        new BEASTState(runName, sourceDirectory, new OutputPaths(outputDirectory, this.overwrite));
                state.setInputObserver((reference, path) -> {
                    try {
                        record.addInput(reference, path);
                    } catch (IOException error) {
                        throw new UncheckedIOException("Could not record an input file.", error);
                    }
                });
                runChecked(checked, state, record, cancellationRequested::get, report);
                console.log().println("Execution and basic output checks completed.");
                console.verify();
            }
            if (cancellationRequested.get() || owner.isInterrupted()) {
                throw new CancellationException("The managed run was interrupted before completion was recorded.");
            }
            record.markSucceeded();
            return record;
        } catch (IOException
                | ParserConfigurationException
                | SAXException
                | RuntimeException
                | java.lang.Error failure) {
            // NIO must be able to write the terminal record even when the caller interrupted this thread.
            restoreInterrupt = Thread.interrupted();
            Throwable reported = failure;
            boolean cancelled =
                    failure instanceof CancellationException || cancellationRequested.get() || restoreInterrupt;
            if (cancelled && !(failure instanceof CancellationException)) {
                reported = new CancellationException("The managed run was interrupted before completion.");
                reported.initCause(failure);
            }
            appendFailure(runDirectory, reported);
            try {
                if (cancelled) record.markCancelled(reported, getDiagnostic(reported));
                else record.markFailed(reported, getDiagnostic(reported));
            } catch (IOException recordFailure) {
                reported.addSuppressed(recordFailure);
            }
            if (cancelled) throw (CancellationException) reported;
            throw failure;
        } finally {
            if (hookRegistered) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException shuttingDown) {
                    // The hook is already running; it will retain or confirm the recorded outcome.
                }
            }
            if (restoreInterrupt) owner.interrupt();
        }
    }

    /** Starts the run record and adds the BEAST 3 libraries, components and adapter bundles. */
    static RunRecord createRunRecord(
            Path runDirectory,
            String source,
            Path sourceFile,
            Path sourceDirectory,
            TileCatalog<BEASTState> catalog,
            List<LocalAdapterBundles.Bundle> adapterBundles)
            throws IOException {
        RunRecord record = RunRecord.create(
                runDirectory,
                source,
                sourceFile,
                sourceDirectory,
                "beast3",
                BEASTVersion2.INSTANCE.getVersionString(),
                PhyloSpecCli.phyloSpecVersion());
        record.update(data -> {
            ObjectNode runtime = (ObjectNode) data.get("runtime");
            ArrayNode bundles = runtime.putArray("adapterBundles");
            for (var bundle : adapterBundles) {
                ObjectNode entry = bundles.addObject();
                entry.put("path", bundle.path());
                entry.put("fingerprintedAt", bundle.fingerprintedAt());
                ArrayNode jars = entry.putArray("jars");
                for (var jar : bundle.jars()) {
                    jars.addObject()
                            .put("path", jar.path())
                            .put("sha256", jar.sha256())
                            .put("size", jar.size())
                            .put("use", jar.use());
                }
            }
            runtime.put(
                    "adapterLoading",
                    "Explicit local bundle inventory, fingerprinted before execution; not a list of every class used. "
                            + "Parent runtime classes take precedence. Host PhyloSpec/BEAST JAR copies are not added. "
                            + "Selected providers and their actual code sources are recorded under libraries. "
                            + "Bundles are not copied; native global registrations are not hot-unloaded.");

            ArrayNode libraries = data.putArray("libraries");
            for (var library : catalog.getLibraries()) {
                Class<?> implementation = library.getClass();
                var codeSource = implementation.getProtectionDomain().getCodeSource();
                libraries
                        .addObject()
                        .put("id", library.getId())
                        .put("class", implementation.getName())
                        .put("version", implementation.getPackage().getImplementationVersion())
                        .put(
                                "location",
                                codeSource == null
                                        ? null
                                        : codeSource.getLocation().toExternalForm());
            }
            ArrayNode components = data.putArray("components");
            for (var component : catalog.getComponentLibraries()) {
                components.addObject().put("name", component.getName()).put("version", component.getVersion());
            }

            data.put(
                    "inputCoverage",
                    "Only input references reported through BEASTState.resolvePath are recorded. Hashes are observed before the native reader and do not prove parsing succeeded. Input data are not copied; this is not a self-contained archive.");
        });
        return record;
    }

    private void runChecked(
            CheckedModel checked,
            BEASTState beastState,
            RunRecord record,
            java.util.function.BooleanSupplier cancellationRequested,
            Consumer<String> progress)
            throws IOException, ParserConfigurationException, SAXException {
        progress.accept("[2/4] Building and initializing the BEAST 3 model...");
        // Native inputs can initialize randomly during tile application, before the MCMC is built.
        boolean seedIsGenerated = this.seed == null && record != null;
        beastState.randomSeed =
                seedIsGenerated ? Long.valueOf(new SecureRandom().nextLong(1, Integer.MAX_VALUE)) : this.seed;
        if (beastState.randomSeed != null) {
            Randomizer.setSeed(beastState.randomSeed);
        }
        if (record != null) {
            record.recordSeed("modelBuilding", beastState.randomSeed, Randomizer.getSeed(), seedIsGenerated);
        }

        MCMC mcmc = record == null ? new MCMC() : new ManagedMCMC(cancellationRequested);
        CompoundDistribution likelihood = buildMcmc(checked, beastState, mcmc);
        long chainLength = beastState.getSamplerSpec().chainLength();
        List<Logger> loggers = mcmc.loggersInput.get();
        String declaredStateFile = beastState.getSamplerSpec().stateFile();
        String stateFile = beastState.resolveOutputPath(
                declaredStateFile != null ? declaredStateFile : OutputPaths.getStateFile(beastState.outputPrefix));
        beastState.prepareOutputFiles(loggers, stateFile, record != null ? record.getDirectory() : null);
        mcmc.setStateFile(stateFile, false);

        // run

        if (beastState.randomSeed != null) {
            Randomizer.setSeed(beastState.randomSeed);
        }
        if (record == null) {
            beastState.initializeBEASTObjects();
            initializeLikelihood(beastState, likelihood);
            mcmc.run();
        } else {
            ManagedMCMC managed = (ManagedMCMC) mcmc;
            record.configure(
                    chainLength, beastState.randomSeed, beastState.runName, getLoggerSettings(loggers), stateFile);
            record.recordSeed("mcmc", beastState.randomSeed, Randomizer.getSeed(), seedIsGenerated);
            try {
                managed.checkCancellation();
                beastState.initializeBEASTObjects();
                initializeLikelihood(beastState, likelihood);
                for (Distribution distribution : beastState.likelihoodDistributions) {
                    if (distribution instanceof ReportingTreeLikelihood treeLikelihood) {
                        var backend = treeLikelihood.backend();
                        recordLikelihoodBackend(record, treeLikelihood.getID(), backend);
                        progress.accept("Likelihood " + treeLikelihood.getID() + ": " + backend.implementation()
                                + (backend.version() == null
                                        ? " (BEAGLE is not active)."
                                        : " " + backend.version() + ", resource " + backend.resourceNumber() + " ("
                                                + backend.resourceName() + ")."));
                    }
                }
                managed.checkCancellation();
                record.markRunning();
                progress.accept("[3/4] Running MCMC (chain length: " + chainLength + ")...");
                managed.run();
                managed.checkCancellation();
                progress.accept("[4/4] Checking result files and final state...");
                managed.verifyLogs();
                verifyOutputs(loggers, Path.of(stateFile), chainLength);
            } catch (IOException
                    | ParserConfigurationException
                    | SAXException
                    | RuntimeException
                    | java.lang.Error failure) {
                boolean interrupted = Thread.interrupted();
                try {
                    managed.close();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
                throw failure;
            }
        }
    }

    static List<RunRecord.LoggerSetting> getLoggerSettings(List<Logger> loggers) {
        List<RunRecord.LoggerSetting> settings = new ArrayList<>();
        for (Logger logger : loggers) {
            String file = logger.fileNameInput.get();
            if (file == null || file.isEmpty()) {
                settings.add(new RunRecord.LoggerSetting("screen", logger.everyInput.get(), null));
            } else {
                String kind = logger.modeInput.get() == Logger.LOGMODE.tree ? "tree" : "log";
                settings.add(new RunRecord.LoggerSetting(kind, logger.everyInput.get(), file));
            }
        }
        return settings;
    }

    /** Records an initialized likelihood, rather than inferring activation from library availability. */
    private static void recordLikelihoodBackend(RunRecord record, String id, ReportingTreeLikelihood.Backend backend)
            throws IOException {
        record.update(data -> ((ObjectNode) data.get("runtime"))
                .withArray("likelihoodBackends")
                .addObject()
                .put("likelihood", id)
                .put("implementation", backend.implementation())
                .put("version", backend.version())
                .put("resourceNumber", backend.resourceNumber())
                .put("resourceName", backend.resourceName())
                .put("resourceFlags", backend.resourceFlags()));
    }

    private static String getDiagnostic(Throwable failure) {
        return failure instanceof PhyloSpecException diagnostic
                ? diagnostic.diagnostic().format()
                : null;
    }

    /** Builds the same BEAST state, distributions, proposals and loggers for execution and export. */
    private CompoundDistribution buildMcmc(CheckedModel checked, BEASTState beastState, MCMC mcmc) {
        try {
            checked.tiles.applyBestTiling(beastState);
        } catch (TileApplicationError error) {
            PhyloSpecDiagnostic diagnostic = new PhyloSpecDiagnostic(
                    PhyloSpecDiagnostic.Stage.MODEL_BUILDING,
                    error.toError(sourceRange(checked.parser, error.getAstNode())),
                    false,
                    source,
                    sourceFile);
            throw new PhyloSpecException(diagnostic, error, checked.warnings);
        }

        checked.catalog.configureState(beastState);

        // add state

        State state = new State();
        beastState.setInput(state, state.stateNodeInput, new ArrayList<>(beastState.stateNodes.keySet()));

        // add distribution

        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(beastState.getAvailableID("prior"));
        beastState.setInput(prior, prior.pDistributions, new ArrayList<>(beastState.priorDistributions.values()));

        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(beastState.getAvailableID("likelihood"));
        beastState.setInput(likelihood, likelihood.pDistributions, beastState.likelihoodDistributions);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(beastState.getAvailableID("posterior"));
        beastState.setInput(posterior, posterior.pDistributions, List.of(prior, likelihood));

        // build loggers

        List<Logger> loggers = beastState.buildLoggers(posterior, prior, likelihood);

        // create MCMC object

        beastState.setInput(
                mcmc, mcmc.chainLengthInput, beastState.getSamplerSpec().chainLength());
        beastState.setInput(mcmc, mcmc.startStateInput, state);
        beastState.setInput(mcmc, mcmc.posteriorInput, posterior);

        beastState.setInput(mcmc, mcmc.operatorsInput, beastState.operators);
        beastState.setInput(mcmc, mcmc.loggersInput, loggers);
        return likelihood;
    }

    private static void initializeLikelihood(BEASTState state, CompoundDistribution likelihood) {
        // Fully observed terms may have no path from a sampled StateNode. BEAST's dirty
        // traversal will never evaluate those terms, so seed their caches before MCMC.
        // Subsequent parameter/tree proposals retain the normal BEAST dirty traversal.
        for (Distribution distribution : state.likelihoodDistributions) distribution.calculateLogP();
        likelihood.calculateLogP();
    }

    static void verifyOutputs(List<Logger> loggers, Path stateFile, long chainLength) throws IOException {
        List<Path> expected = new ArrayList<>();
        for (Logger logger : loggers) {
            String name = logger.fileNameInput.get();
            if (name != null && !name.isEmpty()) expected.add(Path.of(name));
        }
        expected.add(stateFile);
        for (Path path : expected) {
            if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                throw new IOException("Expected output is missing or empty: " + path);
            }
        }
        // BEAST can swallow state-write failures. Reject stale or truncated state XML.
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (var input = Files.newInputStream(stateFile)) {
            var xml = factory.createXMLStreamReader(input);
            try {
                boolean rootSeen = false;
                while (xml.hasNext()) {
                    int event = xml.next();
                    if (event == XMLStreamConstants.DTD) throw new IOException("State output must not contain a DTD.");
                    if (event == XMLStreamConstants.START_ELEMENT && !rootSeen) {
                        rootSeen = true;
                        if (!xml.getLocalName().equals("itsabeastystatewerein")
                                || !Long.toString(chainLength).equals(xml.getAttributeValue(null, "sample"))) {
                            throw new IOException(
                                    "State output does not contain the final sample " + chainLength + ": " + stateFile);
                        }
                    }
                }
                if (!rootSeen) throw new IOException("State output contains no state: " + stateFile);
            } finally {
                xml.close();
            }
        } catch (XMLStreamException malformed) {
            throw new IOException("State output is incomplete or invalid: " + stateFile, malformed);
        }
    }

    private static void appendFailure(Path directory, Throwable failure) {
        StringWriter detail = new StringWriter();
        failure.printStackTrace(new PrintWriter(detail));
        try {
            Files.writeString(
                    directory.resolve("console.log"),
                    detail.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException logFailure) {
            failure.addSuppressed(logFailure);
        }
    }

    private static Range sourceRange(Parser parser, AstNode node) {
        if (node == null) return null;
        Range range = parser.getRangeForAstNode(node);
        return range == null ? node.getRange() : range;
    }

    /**
     * A successful static check. The matched model is kept private so execution can continue from
     * this exact catalog and tiling without exposing mutable AST or native state to callers.
     */
    public static final class CheckedModel {
        private final List<Stmt> statements;
        private final Parser parser;
        private final TileCatalog<BEASTState> catalog;
        private final EvaluateTiles<BEASTState> tiles;
        private final List<PhyloSpecDiagnostic> warnings;

        private CheckedModel(
                List<Stmt> statements,
                Parser parser,
                TileCatalog<BEASTState> catalog,
                EvaluateTiles<BEASTState> tiles,
                List<PhyloSpecDiagnostic> warnings) {
            this.statements = statements;
            this.parser = parser;
            this.catalog = catalog;
            this.tiles = tiles;
            this.warnings = List.copyOf(warnings);
        }

        public List<PhyloSpecDiagnostic> warnings() {
            return warnings;
        }
    }

    /** Each check owns its listener so warnings and stages cannot leak into the next call. */
    private final class Diagnostics implements ErrorEventListener {
        private final List<PhyloSpecDiagnostic> warnings = new ArrayList<>();
        private final Consumer<PhyloSpecDiagnostic> warningHandler;
        private PhyloSpecDiagnostic.Stage stage;

        private Diagnostics(Consumer<PhyloSpecDiagnostic> warningHandler) {
            this.warningHandler = Objects.requireNonNull(warningHandler, "warningHandler");
        }

        private PhyloSpecException failure(Error error, Throwable cause) {
            return new PhyloSpecException(
                    new PhyloSpecDiagnostic(stage, error, false, source, sourceFile), cause, warnings);
        }

        @Override
        public void errorDetected(Error error) {
            // Throwing here also prevents parser recovery from accepting a partial script.
            throw failure(error, error);
        }

        @Override
        public void warningDetected(Error warning) {
            PhyloSpecDiagnostic diagnostic = new PhyloSpecDiagnostic(stage, warning, true, source, sourceFile);
            warnings.add(diagnostic);
            warningHandler.accept(diagnostic);
        }
    }
}
