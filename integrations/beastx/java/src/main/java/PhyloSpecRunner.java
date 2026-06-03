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
import tiling.model.BeastXModelBuilder;
import tiling.mcmc.BeastXMCMCBuilder;
import tiling.BeastXState;
import tiling.runner.BeastXRunMode;
import tiling.runner.BeastXRunResult;
import tiling.runner.BeastXRunnerOptions;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhyloSpecRunner implements ErrorEventListener {

    private final String source;

    public PhyloSpecRunner(String source) {
        this.source = source;
    }

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
        return new BeastXModelBuilder(false).build(beastState);
    }

    public BeastXModel buildMaterializedModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return buildMaterializedModel(beastState);
    }

    public BeastXModel buildMaterializedModel(BeastXState beastState) {
        return new BeastXModelBuilder(true).build(beastState);
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
        return new BeastXMCMCBuilder().build(model);
    }

    public MCMC buildMCMC(BeastXModel model, long chainLength) {
        return new BeastXMCMCBuilder(chainLength).build(model);
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

    public BeastXRunResult run(BeastXRunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        ParsedPhyloSpec parsed =
                parseAndResolve();

        BeastXState beastState =
                tile(parsed, options.runName());

        if (options.mode() == BeastXRunMode.BUILD_STATE) {
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

        BeastXModel model =
                buildModelForOptions(beastState, options);

        if (options.mode() == BeastXRunMode.BUILD_MODEL) {
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

        if (options.mode() == BeastXRunMode.BUILD_MCMC) {
            return run;
        }

        if (options.mode() == BeastXRunMode.EXECUTE_MCMC) {
            mcmc.run();
            return run.asExecuted();
        }

        throw new IllegalStateException("Unsupported BEAST X run mode: " + options.mode());
    }

    public BeastXRunResult buildRun(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.BUILD_MCMC)
                        .build()
        );
    }

    public BeastXRunResult buildRun(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.BUILD_MCMC)
                        .chainLengthOverride(chainLength)
                        .build()
        );
    }

    public BeastXRunResult buildRun(BeastXRunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                options.toBuilder()
                        .mode(BeastXRunMode.BUILD_MCMC)
                        .build()
        );
    }

    public BeastXRunResult buildMaterializedRun(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.BUILD_MCMC)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult buildMaterializedRun(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.BUILD_MCMC)
                        .chainLengthOverride(chainLength)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult execute(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.EXECUTE_MCMC)
                        .build()
        );
    }

    public BeastXRunResult execute(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.EXECUTE_MCMC)
                        .chainLengthOverride(chainLength)
                        .build()
        );
    }

    public BeastXRunResult execute(BeastXRunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                options.toBuilder()
                        .mode(BeastXRunMode.EXECUTE_MCMC)
                        .build()
        );
    }

    public BeastXRunResult executeMaterialized(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.EXECUTE_MCMC)
                        .materializePhyloCTMC(true)
                        .build()
        );
    }

    public BeastXRunResult executeMaterialized(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        return run(
                BeastXRunnerOptions.builder(runName)
                        .mode(BeastXRunMode.EXECUTE_MCMC)
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

    public MCMC runMCMC(BeastXRunnerOptions options)
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

    public void runPhyloSpec(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        execute(runName);
    }

    public void runPhyloSpec(BeastXRunnerOptions options)
            throws IOException, ParserConfigurationException, SAXException {
        execute(options);
    }

    public void runMaterializedPhyloSpec(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        executeMaterialized(runName);
    }

    private BeastXState tile(
            ParsedPhyloSpec parsed,
            String runName
    ) {
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
            BeastXRunnerOptions options
    ) {
        if (options.materializePhyloCTMC()) {
            return buildMaterializedModel(beastState);
        }

        return buildModel(beastState);
    }

    private MCMC buildMCMCForOptions(
            BeastXModel model,
            BeastXRunnerOptions options
    ) {
        if (options.chainLengthOverride() == null) {
            return buildMCMC(model);
        }

        return buildMCMC(model, options.chainLengthOverride());
    }

    private ParsedPhyloSpec parseAndResolve()
            throws IOException {
        ComponentResolver componentResolver =
                loadComponentResolver();

        Lexer lexer =
                new Lexer(this.source);

        lexer.registerEventListener(this);

        List<Token> tokens =
                lexer.scanTokens();

        Parser parser =
                new Parser(tokens);

        parser.registerEventListener(this);

        List<Stmt> statements =
                parser.parse();

        statements =
                new RemoveGroupings().transform(statements);

        statements =
                new EvaluateLiterals().transform(statements);

        statements =
                new EvaluateScalarFunctions().transform(statements);

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

    @Override
    public void errorDetected(Error error) {
        throw new PhyloSpecRunnerException(error.toStdOutString(this.source));
    }

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