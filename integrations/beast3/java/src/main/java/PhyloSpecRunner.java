import beast.base.core.BEASTObject;
import beast.base.inference.*;
import operators.OperatorSelector;
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
import tiles.TileLibrary;
import tiling.BEASTState;
import tiling.EvaluateTiles;
import tiling.TilingError;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhyloSpecRunner implements ErrorEventListener {

    private final String source;

    public PhyloSpecRunner(String source) {
        this.source = source;
    }

    public void runPhyloSpec() {
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

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.getTiles(), variableResolver, stochasticityResolver);
        BEASTState beastState = null;
        try {
            beastState = applyTiles.applyBestTiling(statements);
        } catch (TilingError error) {
            Range range = parser.getRangeForAstNode(error.getAstNode());
            this.errorDetected(error.toError(range));
        }

        // add state

        State state = new State();
        beastState.setInput(state, state.stateNodeInput, new ArrayList<>(beastState.stateNodes.keySet()));

        // add distribution

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(beastState.getID("posterior"));
        beastState.setInput(posterior, posterior.pDistributions, new ArrayList<>(beastState.distributions.values()));

        // add operators

        for (StateNode stateNode : beastState.stateNodes.keySet()) {
            OperatorSelector.addDefaultOperators(stateNode, beastState);
        }

        // add loggers

        List<BEASTObject> loggedObjects = new ArrayList<>();
        loggedObjects.add(posterior);
        loggedObjects.addAll(beastState.stateNodes.keySet());

        Logger screenLogger = new Logger();
        beastState.setInput(screenLogger, screenLogger.loggersInput, loggedObjects);
        beastState.setInput(screenLogger, screenLogger.everyInput, 1000);

        Logger fileLogger = new Logger();
        beastState.setInput(fileLogger, fileLogger.loggersInput, loggedObjects);
        beastState.setInput(fileLogger, fileLogger.everyInput, 1000);
        beastState.setInput(fileLogger, fileLogger.fileNameInput, "logs.log");

        // create MCMC object

        MCMC mcmc = new MCMC();
        beastState.setInput(mcmc, mcmc.chainLengthInput, (long) 100_000);
        beastState.setInput(mcmc, mcmc.startStateInput, state);
        beastState.setInput(mcmc, mcmc.posteriorInput, posterior);
        beastState.setInput(mcmc, mcmc.operatorsInput, new ArrayList<>(beastState.operators.keySet()));
        beastState.setInput(mcmc, mcmc.loggersInput, List.of(screenLogger, fileLogger));

        // run

        beastState.initializeBEASTObjects();

        try {
            mcmc.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static ComponentResolver loadComponentResolver() {
        try {
            List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
            return new ComponentResolver(componentLibraries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void errorDetected(Error error) {
        System.out.println(error.toStdOutString(this.source));
        System.exit(1);
    }
}
