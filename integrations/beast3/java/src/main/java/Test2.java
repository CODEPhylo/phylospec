import beast.base.core.BEASTObject;
import beast.base.inference.*;
import operators.OperatorSelector;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.xml.sax.SAXException;
import tiling.BEASTState;
import tiling.EvaluateTiles;
import tiles.TileLibrary;

import javax.print.StreamPrintService;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Test2 {
    static void main(String[] args) {
        String source = """
        Alignment data = fromNexus("/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex")
        
        Tree tree ~ Yule(
            birthRate=1.0, taxa=taxa(data)
        )
        
        Alignment alignment ~ PhyloCTMC(
          tree,
          branchRates~StrictClock(rate=1.0, tree=tree),
          qMatrix=jc69(),
          siteRates~DiscreteGammaInv(
            shape=1.0, numCategories=4, numSites=100
          )
        ) observed as data
        """;

        ComponentResolver componentResolver = loadComponentResolver();

         // run lexer

        List<Token> tokens = new Lexer(source).scanTokens();

        // run parser

        List<Stmt> statements = new Parser(tokens).parse();

        // simplify graph

        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        // run variable resolver

        VariableResolver variableResolver = new VariableResolver(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.getTiles(), variableResolver, stochasticityResolver);
        BEASTState beastState = applyTiles.applyBestTiling(statements);

        // create MCMC object

        MCMC mcmc = new MCMC();
        beastState.setInput(mcmc, mcmc.chainLengthInput, (long) 100_000);

        // add state

        State state = new State();
        beastState.setInput(state, state.stateNodeInput, new ArrayList<>(beastState.stateNodes.keySet()));
        beastState.setInput(mcmc, mcmc.startStateInput, state);

        // add distribution

        CompoundDistribution posterior = new CompoundDistribution();
        beastState.setInput(posterior, posterior.pDistributions, new ArrayList<>(beastState.distributions.values()));
        beastState.setInput(mcmc, mcmc.posteriorInput, posterior);

        // add operators

        for (StateNode stateNode : beastState.distributions.keySet()) {
            OperatorSelector.addOperators(stateNode, beastState);
        }

        beastState.setInput(mcmc, mcmc.operatorsInput, new ArrayList<>(beastState.operators));

        // add loggers

        List<BEASTObject> loggedObjects = new ArrayList<>();
        loggedObjects.addAll(beastState.stateNodes.keySet());
        loggedObjects.addAll(beastState.distributions.values());

        Logger logger = new Logger();
        beastState.setInput(logger, logger.loggersInput, loggedObjects);
        beastState.setInput(logger, logger.everyInput, 1000);
        beastState.setInput(mcmc, mcmc.loggersInput, List.of(logger));

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
        List<ComponentLibrary> componentLibraries = null;
        try {
            componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ComponentResolver(componentLibraries);
    }

}
