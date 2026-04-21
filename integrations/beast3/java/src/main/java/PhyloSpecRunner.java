import beast.base.inference.*;
import beastconfig.LoggerSelector;
import beastconfig.OperatorSelector;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import tiles.OperatorTileLibrary;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Range;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.xml.sax.SAXException;
import tiles.BeastCoreTileLibrary;
import beastconfig.BEASTState;
import tiles.TileLibrary;
import tiling.EvaluateTiles;
import tiling.TileApplicationError;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/// Top-level entry point for running a PhyloSpec model against BEAST 3.
///
/// Orchestrates the full pipeline: lexing and parsing the source, simplifying the AST,
/// resolving variables and types, applying tiles to build a BEAST object graph, assembling
/// the MCMC run (state, distributions, operators, loggers), and finally executing it.
public class PhyloSpecRunner implements ErrorEventListener {

    private final String source;

    /**
     * Constructs a runner for the given PhyloSpec source code.
     */
    public PhyloSpecRunner(String source) {
        this.source = source;
    }

    /**
     * Runs the full PhyloSpec-to-BEAST pipeline for the given run name.
     * Any error detected during lexing, parsing, type resolution, or tiling is reported
     * via {@link #errorDetected} and terminates the process immediately.
     */
    public void runPhyloSpec(String runName) throws IOException, ParserConfigurationException, SAXException {
        ComponentResolver componentResolver = loadComponentResolver();

        // run lexer

        Lexer lexer = new Lexer(this.source);
        lexer.registerEventListener(this);
        List<Token> tokens = lexer.scanTokens();

        // run parser

        Parser parser = new Parser(tokens);
        parser.registerEventListener(this);
        List<Stmt> statements = parser.parse();

        // simplify graph

        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);
        statements = new EvaluateScalarFunctions().transform(statements);

        // run variable resolver

        VariableResolver variableResolver = new VariableResolver(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(componentResolver);

        try {
            typeResolver.visitStatements(statements);
        } catch (TypeError error) {
            Range range = parser.getRangeForAstNode(error.getAstNode());
            this.errorDetected(error.toError(range));
        }

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.loadAll(), OperatorTileLibrary.getTiles(), variableResolver, stochasticityResolver);
        BEASTState beastState = new BEASTState(runName);
        try {
            applyTiles.getBestTiling(statements);
            beastState = applyTiles.applyBestTiling(beastState);
        } catch (TileApplicationError error) {
            Range range = parser.getRangeForAstNode(error.getAstNode());
            this.errorDetected(error.toError(range));
        }

        // add state

        State state = new State();
        beastState.setInput(state, state.stateNodeInput, new ArrayList<>(beastState.stateNodes.keySet()));

        // add distribution

        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(beastState.getAvailableID("prior"));
        beastState.setInput(prior, prior.pDistributions, new ArrayList<>(beastState.priorDistributions.values()));

        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(beastState.getAvailableID("likelihood"));
        beastState.setInput(likelihood, likelihood.pDistributions, new ArrayList<>(beastState.likelihoodDistributions.values()));

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(beastState.getAvailableID("posterior"));
        beastState.setInput(posterior, posterior.pDistributions, List.of(prior, likelihood));

        // add operators

        for (StateNode stateNode : beastState.stateNodes.keySet()) {
            OperatorSelector.addDefaultOperators(stateNode, beastState);
        }

        // add loggers

        LoggerSelector.addMissingLoggers(beastState, posterior, prior, likelihood);

        // create MCMC object

        MCMC mcmc = new MCMC();
        beastState.setInput(mcmc, mcmc.chainLengthInput, beastState.chainLength);
        beastState.setInput(mcmc, mcmc.startStateInput, state);
        beastState.setInput(mcmc, mcmc.posteriorInput, posterior);
        beastState.setInput(mcmc, mcmc.operatorsInput, new ArrayList<>(beastState.operators.keySet()));
        beastState.setInput(mcmc, mcmc.loggersInput, beastState.getLoggers());

        // run

        beastState.initializeBEASTObjects();

        mcmc.run();
    }

    /**
     * Loads the core component libraries (built-in types and generators) and returns a
     * resolver backed by them.
     */
    private static ComponentResolver loadComponentResolver() {
        try {
            List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
            return new ComponentResolver(componentLibraries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prints the error to standard output and exits the process.
     */
    @Override
    public void errorDetected(Error error) {
        System.out.println(error.toStdOutString(this.source));
        System.exit(1);
    }
}
