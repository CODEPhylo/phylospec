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
import tiles.BeastXTileLibraries;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunResult;
import tiling.runner.XmlRunnerOptions;
import tiling.runner.FileRunPaths;
import tiling.runner.BeastXRunPipeline;

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

    private final BeastXRunPipeline runPipeline;

    /**
     * Creates a runner for the given PhyloSpec source string.
     */
    public PhyloSpecRunner(String source) {
        this.source =
                source;

        this.runPipeline =
                new BeastXRunPipeline();
    }

    /* --- Static convenient methods for easy creation of a runner.  --- */

    /**
     * Creates a runner from a PhyloSpec source file using UTF-8 encoding.
     */
    public static PhyloSpecRunner fromFile(Path sourcePath)
            throws IOException {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath must not be null.");
        }

        return new PhyloSpecRunner(
                Files.readString(sourcePath, StandardCharsets.UTF_8)
        );
    }

    /**
     * Builds an XML run for a source file and writes it to the requested path.
     */
    public static XmlRunResult buildXmlRunFromFile(
            Path sourcePath,
            Path xmlPath
    ) throws Exception {
        String runName =
                FileRunPaths.defaultRunName(sourcePath);

        return fromFile(sourcePath)
                .buildXmlRun(
                        XmlRunnerOptions.builder(runName, xmlPath)
                                .build()
                );
    }

    /**
     * Executes the default XML run for a source file under target/beastx-runs.
     */
    public static XmlRunResult executeDefaultXmlRunFromFile(Path sourcePath)
            throws Exception {
        FileRunPaths paths = FileRunPaths.forSource(
                sourcePath,
                Path.of("target", "beastx-runs")
        );

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

    /* --- Public methods executing different parts of the pipeline. --- */

    /**
     * Runs the PhyloSpec-to-BEAST X pipeline according to the requested run mode.
     */
    public BeastXRunResult run(RunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        ParsedPhyloSpec parsed =
                parseAndResolve();

        BeastXState beastState =
                tile(parsed, options.runName());

        return this.runPipeline
                .run(beastState, options);
    }

    /**
     * Parses, resolves, and tiles the PhyloSpec source into a BEAST X state.
     */
    public BeastXState buildState(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        ParsedPhyloSpec parsed =
                parseAndResolve();

        return tile(parsed, runName);
    }

    /**
     * Builds a BEAST X model from the PhyloSpec source.
     */
    public BeastXModel buildModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return this.runPipeline
                .buildModel(beastState, false);
    }

    /**
     * Builds a BEAST X model with materialized PhyloCTMC likelihoods.
     */
    public BeastXModel buildMaterializedModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return this.runPipeline
                .buildModel(beastState, true);
    }

    /**
     * Builds an MCMC object for the source using the requested chain length.
     */
    public MCMC buildMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        return this.runPipeline
                .buildMCMC(model, chainLength);
    }

    /**
     * Builds an MCMC run with materialized PhyloCTMC likelihoods without executing it.
     */
    public BeastXRunResult buildMaterializedRun(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.BUILD_MCMC)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    /**
     * Builds and executes an in-memory BEAST X MCMC run.
     */
    public MCMC runMCMC(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                RunnerOptions.builder(runName)
                        .mode(RunMode.EXECUTE_MCMC)
                        .build()
        ).mcmc();
    }

    /**
     * Writes XML, parses it through the BEAST X XML parser, and optionally executes it.
     */
    public XmlRunResult runXml(XmlRunnerOptions options)
            throws Exception {
        BeastXModel model =
                options.materializePhyloCTMC()
                        ? buildMaterializedModel(options.runName())
                        : buildModel(options.runName());

        return this.runPipeline
                .runXml(model, options);
    }

    /**
     * Writes the generated BEAST X XML to disk and returns the model used for export.
     */
    public BeastXModel writeXml(
            String runName,
            Path xmlPath
    ) throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        this.runPipeline
                .writeXml(model, xmlPath);

        return model;
    }

    /**
     * Writes BEAST X XML and executes the parsed XML MCMC.
     */
    public MCMC writeAndRunXmlMCMC(
            String runName,
            Path xmlPath
    ) throws Exception {
        writeXml(runName, xmlPath);

        return this.runPipeline
                .runXmlMCMC(xmlPath);
    }

    /**
     * Writes BEAST X XML and parses it into an XML run without executing it.
     */
    public XmlRunResult buildXmlRun(
            String runName,
            Path xmlPath
    ) throws Exception {
        BeastXModel model =
                writeXml(runName, xmlPath);

        MCMC mcmc =
                this.runPipeline
                        .parseXmlMCMC(xmlPath);

        return new XmlRunResult(
                runName,
                model,
                xmlPath,
                mcmc,
                false
        );
    }

    /**
     * Writes and parses an XML run without executing it.
     */
    public XmlRunResult buildXmlRun(XmlRunnerOptions options)
            throws Exception {
        return runXml(
                options.toBuilder()
                        .execute(false)
                        .build()
        );
    }

    /**
     * Writes, parses, and executes a BEAST X XML run.
     */
    public XmlRunResult executeXmlRun(
            String runName,
            Path xmlPath
    ) throws Exception {
        XmlRunResult run =
                buildXmlRun(runName, xmlPath);

        run.mcmc().run();

        return run.asExecuted();
    }

    /**
     * Writes, parses, and executes a BEAST X XML run.
     */
    public XmlRunResult executeXmlRun(XmlRunnerOptions options)
            throws Exception {
        return runXml(
                options.toBuilder()
                        .execute(true)
                        .build()
        );
    }

    /* --- Internal methods parsing a PhyloSpec script and executing the tiling algorithm --- */

    /**
     * Parses the PhyloSpec source and prepares it for tiling.
     *
     * This includes lexical scanning, parsing, AST simplification, variable
     * resolution, type checking, and stochasticity analysis.
     */
    private ParsedPhyloSpec parseAndResolve() {
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
                        BeastXTileLibraries.loadAll(),
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
