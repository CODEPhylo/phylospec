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
import tiling.BeastXModelBuilder;
import tiling.BeastXMCMCBuilder;
import tiling.BeastXState;

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

    public BeastXModel buildModel(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXState beastState =
                buildState(runName);

        return buildModel(beastState);
    }

    public BeastXModel buildModel(BeastXState beastState) {
        return new BeastXModelBuilder().build(beastState);
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

    public MCMC buildMCMC(String runName, long chainLength)
            throws IOException, ParserConfigurationException, SAXException {
        BeastXModel model =
                buildModel(runName);

        return buildMCMC(model, chainLength);
    }

    public MCMC buildMCMC(BeastXModel model, long chainLength) {
        return new BeastXMCMCBuilder(chainLength).build(model);
    }

    public void runPhyloSpec(String runName)
            throws IOException, ParserConfigurationException, SAXException {
        buildModel(runName);
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