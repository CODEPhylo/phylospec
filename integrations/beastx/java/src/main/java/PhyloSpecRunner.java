import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.errors.Error;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.errors.TileApplicationError;
import tiles.BeastXCoreTileLibrary;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.*;
import org.phylospec.errors.*;
import org.phylospec.lexer.*;
import org.phylospec.parser.*;
import org.phylospec.typeresolver.*;
import org.xml.sax.SAXException;
import tiling.BeastXState;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/// Top-level entry point for running a PhyloSpec model using BEAST X.
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

        EvaluateTiles<BeastXState> applyTiles = new EvaluateTiles<>(new BeastXCoreTileLibrary().getTiles(), new ArrayList<>(), variableResolver, stochasticityResolver);
        BeastXState beastState = new BeastXState(runName);
        try {
            applyTiles.getBestTiling(statements);
            beastState = applyTiles.applyBestTiling(beastState);
        } catch (TileApplicationError error) {
            Range range = parser.getRangeForAstNode(error.getAstNode());
            this.errorDetected(error.toError(range));
        }

        // TODO: build BEAST X objects using beastState and run it
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
