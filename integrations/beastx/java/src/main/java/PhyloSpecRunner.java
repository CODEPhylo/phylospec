import dr.inference.mcmc.MCMC;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.xml.sax.SAXException;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXModel;
import tiling.model.ModelBuilder;
import tiling.mcmc.MCMCBuilder;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunResult;
import tiling.runner.XmlRunnerOptions;
import tiling.runner.FileRunPaths;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/*
* Entry point for running PhyloSpec models with the BEAST X backend.
*
* This class coordinates the full pipeline:
* PhyloSpec source -> lexer/parser -> AST transforms -> type resolution
* BEAST X tiling -> BeastXModel -> MCMC or XML execution.
* */
public class PhyloSpecRunner implements ErrorEventListener {

    private final String source;

    public PhyloSpecRunner(String source) {
        this.source = source;
    }

    // Creates a runner from a PhyloSpec source file using UTF-8 encoding.
    public static PhyloSpecRunner fromFile(Path sourcePath)
            throws IOException {
        return fromFile(sourcePath, StandardCharsets.UTF_8);
    }

    public static PhyloSpecRunner fromFile(
            Path sourcePath,
            Charset charset
    ) throws IOException {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath must not be null.");
        }

        if (charset == null) {
            throw new IllegalArgumentException("charset must not be null.");
        }

        return new PhyloSpecRunner(
                Files.readString(sourcePath, charset)
        );
    }
    //  Uses the source file name without its extension as the default run name.
    public static String defaultRunName(Path sourcePath) {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath must not be null.");
        }

        String fileName =
                sourcePath.getFileName().toString();

        int extensionStart =
                fileName.lastIndexOf('.');

        if (extensionStart <= 0) {
            return fileName;
        }

        return fileName.substring(0, extensionStart);
    }

    public static BeastXRunResult buildRunFromFile(Path sourcePath)
            throws IOException, ParserConfigurationException, SAXException {
        String runName =
                defaultRunName(sourcePath);

        return fromFile(sourcePath)
                .buildRun(runName);
    }

    public static BeastXRunResult executeFromFile(Path sourcePath)
            throws IOException, ParserConfigurationException, SAXException {
        String runName =
                defaultRunName(sourcePath);

        return fromFile(sourcePath)
                .execute(runName);
    }

    public static XmlRunResult buildXmlRunFromFile(
            Path sourcePath,
            Path xmlPath
    ) throws Exception {
        String runName =
                defaultRunName(sourcePath);

        return fromFile(sourcePath)
                .buildXmlRun(
                        XmlRunnerOptions.builder(runName, xmlPath)
                                .build()
                );
    }

    public static FileRunPaths defaultOutputPathsForFile(Path sourcePath) {
        return FileRunPaths.forSource(
                sourcePath,
                Path.of("target", "beastx-runs")
        );
    }

    public static XmlRunResult buildDefaultXmlRunFromFile(Path sourcePath)
            throws Exception {
        FileRunPaths paths =
                defaultOutputPathsForFile(sourcePath);

        return fromFile(sourcePath)
                .buildXmlRun(
                        XmlRunnerOptions.builder(
                                        paths.runName(),
                                        paths.xmlPath()
                                )
                                .build()
                );
    }

    public static XmlRunResult executeDefaultXmlRunFromFile(Path sourcePath)
            throws Exception {
        FileRunPaths paths =
                defaultOutputPathsForFile(sourcePath);

        return fromFile(sourcePath)
                .executeXmlRun(
                        XmlRunnerOptions.builder(
                                        paths.runName(),
                                        paths.xmlPath()
                                )
                                .execute(true)
                                .build()
                );
    }

    public static XmlRunResult buildXmlRunFromFileInOutputRoot(
            Path sourcePath,
            Path outputRoot
    ) throws Exception {
        FileRunPaths paths =
                FileRunPaths.forSource(sourcePath, outputRoot);

        return fromFile(sourcePath)
                .buildXmlRun(
                        XmlRunnerOptions.builder(
                                        paths.runName(),
                                        paths.xmlPath()
                                )
                                .build()
                );
    }

    public static XmlRunResult executeXmlRunFromFileInOutputRoot(
            Path sourcePath,
            Path outputRoot
    ) throws Exception {
        FileRunPaths paths =
                FileRunPaths.forSource(sourcePath, outputRoot);

        return fromFile(sourcePath)
                .executeXmlRun(
                        XmlRunnerOptions.builder(
                                        paths.runName(),
                                        paths.xmlPath()
                                )
                                .execute(true)
                                .build()
                );
    }

    public static XmlRunResult executeXmlRunFromFile(
            Path sourcePath,
            Path xmlPath
    ) throws Exception {
        String runName =
                defaultRunName(sourcePath);

        return fromFile(sourcePath)
                .executeXmlRun(
                        XmlRunnerOptions.builder(runName, xmlPath)
                                .execute(true)
                                .build()
                );
    }

    /// Parses, resolves, and tiles the PhyloSpec source into a BEAST X state.
    public BeastXState buildState(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        ParsedPhyloSpec parsed =
                parseAndResolve();

        return tile(parsed, runName);
    }

    public BeastXModel buildModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return buildModel(beastState);
    }

    public BeastXModel buildModel(BeastXState beastState) {
        return new ModelBuilder(false).build(beastState);
    }

    public BeastXModel buildMaterializedModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return buildMaterializedModel(beastState);
    }

    public BeastXModel buildMaterializedModel(BeastXState beastState) {
        return new ModelBuilder(true).build(beastState);
    }

    public MCMC buildMCMC(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        return buildMCMC(model);
    }

    public MCMC buildMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        return buildMCMC(model, chainLength);
    }

    public MCMC buildMCMC(BeastXModel model) {
        return new MCMCBuilder().build(model);
    }

    public MCMC buildMCMC(BeastXModel model, long chainLength) {
        return new MCMCBuilder(chainLength).build(model);
    }

    public MCMC buildMaterializedMCMC(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildMaterializedModel(runName);

        return buildMCMC(model);
    }

    public MCMC buildMaterializedMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildMaterializedModel(runName);

        return buildMCMC(model, chainLength);
    }

    public MCMC buildMaterializedMCMC(BeastXState beastState) {
        BeastXModel model =
                buildMaterializedModel(beastState);

        return buildMCMC(model);
    }

    public MCMC buildMaterializedMCMC(BeastXState beastState, long chainLength) {
        BeastXModel model =
                buildMaterializedModel(beastState);

        return buildMCMC(model, chainLength);
    }

    /// Runs the PhyloSpec-to-BEAST X pipeline according to the requested run mode.
    ///
    /// The pipeline can stop after building the backend state, model, or MCMC object,
    /// or it can execute the MCMC immediately.
    public BeastXRunResult run(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        ParsedPhyloSpec parsed =
                parseAndResolve();

        BeastXState beastState =
                tile(parsed, options.runName());

        options.applyTo(beastState);

        // Stop after tiling if only the backend state is requested.
        if (options.mode() == RunMode.BUILD_STATE) {
            return new BeastXRunResult(
                    options.runName(),
                    options,
                    beastState,
                    null,
                    null,
                    options.materializePhyloCTMC(),
                    false
            );
        }

        // Convert the tiled backend state into a BEAST X model.
        BeastXModel model =
                buildModelForOptions(beastState, options);

        if (options.mode() == RunMode.BUILD_MODEL) {
            return new BeastXRunResult(
                    options.runName(),
                    options,
                    beastState,
                    model,
                    null,
                    options.materializePhyloCTMC(),
                    false
            );
        }

        // Build the BEAST X MCMC object from the model.
        MCMC mcmc =
                buildMCMCForOptions(model, options);

        BeastXRunResult run =
                new BeastXRunResult(
                        options.runName(),
                        options,
                        beastState,
                        model,
                        mcmc,
                        options.materializePhyloCTMC(),
                        false
                );

        if (options.mode() == RunMode.BUILD_MCMC) {
            return run;
        }

        // Execute the BEAST X MCMC only in EXECUTE_MCMC mode.
        if (options.mode() == RunMode.EXECUTE_MCMC) {
            mcmc.run();
            return run.asExecuted();
        }

        throw new IllegalStateException("Unsupported BEAST X run mode: " + options.mode());
    }

    public BeastXRunResult buildRun(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.BUILD_MCMC)
                        .build()
        );
    }

    public BeastXRunResult buildRun(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.BUILD_MCMC)
                        .chainLengthOverride(chainLength)
                        .build()
        );
    }

    public BeastXRunResult buildRun(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                options.toBuilder()
                        .mode(RunMode.BUILD_MCMC)
                        .build()
        );
    }

    public BeastXRunResult buildMaterializedRun(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.BUILD_MCMC)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult buildMaterializedRun(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.BUILD_MCMC)
                        .chainLengthOverride(chainLength)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult execute(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.EXECUTE_MCMC)
                        .build()
        );
    }

    public BeastXRunResult execute(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(chainLength)
                        .build()
        );
    }

    public BeastXRunResult execute(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                options.toBuilder()
                        .mode(RunMode.EXECUTE_MCMC)
                        .build()
        );
    }

    public BeastXRunResult executeMaterialized(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.EXECUTE_MCMC)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult executeMaterialized(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.EXECUTE_MCMC)
                        .chainLengthOverride(chainLength)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public MCMC runMCMC(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return execute(runName).mcmc();
    }

    public MCMC runMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return execute(runName, chainLength).mcmc();
    }

    public MCMC runMCMC(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        return execute(options).mcmc();
    }

    public MCMC runMCMC(BeastXModel model) {
        MCMC mcmc =
                buildMCMC(model);

        mcmc.run();

        return mcmc;
    }

    public MCMC runMCMC(BeastXModel model, long chainLength) {
        MCMC mcmc =
                buildMCMC(model, chainLength);

        mcmc.run();

        return mcmc;
    }

    public MCMC runMaterializedMCMC(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return executeMaterialized(runName).mcmc();
    }

    public MCMC runMaterializedMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return executeMaterialized(runName, chainLength).mcmc();
    }

    public MCMC runMaterializedMCMC(BeastXState beastState) {
        MCMC mcmc =
                buildMaterializedMCMC(beastState);

        mcmc.run();

        return mcmc;
    }

    public MCMC runMaterializedMCMC(BeastXState beastState, long chainLength) {
        MCMC mcmc =
                buildMaterializedMCMC(beastState, chainLength);

        mcmc.run();

        return mcmc;
    }

    public String toXml(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        return toXml(model);
    }

    /**
     * Serializes a BEAST X model into BEAST X XML.
     */
    public String toXml(BeastXModel model) {
        return new StateXmlGenerator()
                .toXml(model);
    }

    /**
     * Writes the generated BEAST X XML to disk.
     */
    public BeastXModel writeXml(
            String runName,
            Path xmlPath
    ) throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        writeXml(model, xmlPath);

        return model;
    }

    public void writeXml(
            BeastXModel model,
            Path xmlPath
    ) throws IOException {
        new StateXmlGenerator()
                .write(model, xmlPath);
    }

    /**
     * Parses a BEAST X XML file into an executable MCMC object.
     */
    public MCMC parseXmlMCMC(Path xmlPath) throws Exception {
        return new XmlRunner()
                .parse(xmlPath);
    }

    public MCMC runXmlMCMC(Path xmlPath) throws Exception {
        return new XmlRunner()
                .run(xmlPath);
    }

    public MCMC writeAndParseXmlMCMC(
            String runName,
            Path xmlPath
    ) throws Exception {
        writeXml(runName, xmlPath);

        return parseXmlMCMC(xmlPath);
    }

    public MCMC writeAndRunXmlMCMC(
            String runName,
            Path xmlPath
    ) throws Exception {
        writeXml(runName, xmlPath);

        return runXmlMCMC(xmlPath);
    }

    public MCMC writeAndRunXmlMCMC(
            BeastXModel model,
            Path xmlPath
    ) throws Exception {
        writeXml(model, xmlPath);

        return runXmlMCMC(xmlPath);
    }

    public XmlRunResult buildXmlRun(
            String runName,
            Path xmlPath
    ) throws Exception {
        BeastXModel model =
                writeXml(runName, xmlPath);

        MCMC mcmc =
                parseXmlMCMC(xmlPath);

        return new XmlRunResult(
                runName,
                model,
                xmlPath,
                mcmc,
                false
        );
    }

    public XmlRunResult executeXmlRun(
            String runName,
            Path xmlPath
    ) throws Exception {
        XmlRunResult run =
                buildXmlRun(runName, xmlPath);

        run.mcmc().run();

        return run.asExecuted();
    }

    public XmlRunResult buildXmlRun(
            BeastXModel model,
            String runName,
            Path xmlPath
    ) throws Exception {
        writeXml(model, xmlPath);

        MCMC mcmc =
                parseXmlMCMC(xmlPath);

        return new XmlRunResult(
                runName,
                model,
                xmlPath,
                mcmc,
                false
        );
    }

    public XmlRunResult executeXmlRun(
            BeastXModel model,
            String runName,
            Path xmlPath
    ) throws Exception {
        XmlRunResult run =
                buildXmlRun(model, runName, xmlPath);

        run.mcmc().run();

        return run.asExecuted();
    }

    /**
     * Writes XML, parses it through the BEAST X XML parser, and optionally executes it.
     */
    public XmlRunResult runXml(XmlRunnerOptions options)
            throws Exception {
        // Build the model that will be exported to XML.
        BeastXModel model =
                options.materializePhyloCTMC()
                        ? buildMaterializedModel(options.runName())
                        : buildModel(options.runName());

        // Export the model before asking BEAST X to parse it back.
        writeXml(model, options.xmlPath());

        // Validate the generated XML by parsing it into a BEAST X MCMC object.
        MCMC mcmc =
                parseXmlMCMC(options.xmlPath());

        XmlRunResult run =
                new XmlRunResult(
                        options.runName(),
                        model,
                        options.xmlPath(),
                        mcmc,
                        false
                );

        if (!options.execute()) {
            return run;
        }

        mcmc.run();

        return run.asExecuted();
    }

    public XmlRunResult buildXmlRun(XmlRunnerOptions options)
            throws Exception {
        return runXml(
                options.toBuilder()
                        .execute(false)
                        .build()
        );
    }

    public XmlRunResult executeXmlRun(XmlRunnerOptions options)
            throws Exception {
        return runXml(
                options.toBuilder()
                        .execute(true)
                        .build()
        );
    }

    public void runPhyloSpec(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        execute(runName);
    }

    public void runPhyloSpec(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        execute(options);
    }

    public void runMaterializedPhyloSpec(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        executeMaterialized(runName);
    }

    /**
     * Applies the BEAST X tile library to the resolved PhyloSpec AST.
     *
     * The resulting BeastXState is the backend-specific intermediate state used
     * later to build a BEAST X model, MCMC object, or XML file.
     */
    private BeastXState tile(
            ParsedPhyloSpec parsed,
            String runName
    ) {
        // Load all BEAST X backend tiles and prepare the tiling evaluator.
        EvaluateTiles<BeastXState> applyTiles =
                new EvaluateTiles<>(
                        new BeastXCoreTileLibrary().getTiles(),
                        new ArrayList<>(),
                        parsed.variableResolver,
                        parsed.stochasticityResolver
                );

        BeastXState beastState =
                new BeastXState(runName);

        try {
            // Find and apply the best tile sequence for the parsed PhyloSpec statements.
            applyTiles.getBestTiling(parsed.statements);
            return applyTiles.applyBestTiling(beastState);
        } catch (TileApplicationError error) {
            Range range =
                    parsed.parser.getRangeForAstNode(error.getAstNode());

            this.errorDetected(error.toError(range));
            throw new IllegalStateException("Unreachable after errorDetected.");
        }
    }

    private BeastXModel buildModelForOptions(
            BeastXState beastState,
            RunnerOptions options
    ) {
        if (options.materializePhyloCTMC()) {
            return buildMaterializedModel(beastState);
        }

        return buildModel(beastState);
    }

    private MCMC buildMCMCForOptions(
            BeastXModel model,
            RunnerOptions options
    ) {
        if (options.chainLengthOverride() == null) {
            return buildMCMC(model);
        }

        return buildMCMC(model, options.chainLengthOverride());
    }

    /**
     * Parses the PhyloSpec source and prepares it for tiling.
     *
     * This includes lexical scanning, parsing, AST simplification, variable
     * resolution, type checking, and stochasticity analysis.
     */
    private ParsedPhyloSpec parseAndResolve()
            throws IOException {
        ComponentResolver componentResolver =
                loadComponentResolver();

        // Tokenize the PhyloSpec source.
        Lexer lexer =
                new Lexer(this.source);

        lexer.registerEventListener(this);

        List<Token> tokens =
                lexer.scanTokens();

        // Parse tokens into PhyloSpec AST statements.
        Parser parser =
                new Parser(tokens);

        parser.registerEventListener(this);

        List<Stmt> statements =
                parser.parse();

        // Simplify the AST before type checking and tiling.
        statements =
                new RemoveGroupings().transform(statements);

        statements =
                new EvaluateLiterals().transform(statements);

        statements =
                new EvaluateScalarFunctions().transform(statements);

        // Resolve variable references and validate component types.
        VariableResolver variableResolver =
                new VariableResolver(statements);

        TypeResolver typeResolver =
                new TypeResolver(componentResolver);

        try {
            typeResolver.visitStatements(statements);
        } catch (TypeError error) {
            Range range =
                    parser.getRangeForAstNode(error.getAstNode());

            this.errorDetected(error.toError(range));
            throw new IllegalStateException("Unreachable after errorDetected.");
        }

        // Determine which statements or expressions are stochastic.
        StochasticityResolver stochasticityResolver =
                new StochasticityResolver();

        stochasticityResolver.visitStatements(statements);

        return new ParsedPhyloSpec(
                parser,
                statements,
                variableResolver,
                stochasticityResolver
        );
    }

    private static ComponentResolver loadComponentResolver() {
        try {
            List<ComponentLibrary> componentLibraries =
                    ComponentResolver.loadCoreComponentLibraries();

            return new ComponentResolver(componentLibraries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts frontend or tiling errors into runner-level exceptions with
     * source-location information.
     */
    @Override
    public void errorDetected(Error error) {
        throw new PhyloSpecRunnerException(error.toStdOutString(this.source));
    }

    /**
     * Internal container for the parsed and resolved PhyloSpec program.
     *
     * It keeps the parser so that later tiling errors can still be mapped back
     * to source-code ranges.
     */
    private static class ParsedPhyloSpec {
        private final Parser parser;
        private final List<Stmt> statements;
        private final VariableResolver variableResolver;
        private final StochasticityResolver stochasticityResolver;

        private ParsedPhyloSpec(
                Parser parser,
                List<Stmt> statements,
                VariableResolver variableResolver,
                StochasticityResolver stochasticityResolver
        ) {
            this.parser =
                    parser;

            this.statements =
                    statements;

            this.variableResolver =
                    variableResolver;

            this.stochasticityResolver =
                    stochasticityResolver;
        }
    }
}