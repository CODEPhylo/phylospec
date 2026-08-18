package mcmc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import beast.base.core.BEASTObject;
import beast.base.evolution.tree.Tree;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Logger;
import beastconfig.BEASTState;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;

/// Tests the logger specs registered by the `mcmc` block tiles and the loggers that
/// [beastconfig.LoggerSelector] builds from them.
public class LoggerTest {

    private static final String MODEL =
            """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Rate birthRate ~ LogNormal(logMean=1.0, logSd=0.5)
            Tree tree ~ Yule(birthRate=birthRate, taxa=taxa(data))
            """;

    /// A tiled state together with the distributions that the loggers are built against.
    private record Built(
            BEASTState state,
            CompoundDistribution posterior,
            CompoundDistribution prior,
            CompoundDistribution likelihood) {}

    @Test
    public void testDefaultLoggersWhenNoMcmcBlockIsGiven() {
        Built built = tile(MODEL);
        List<Logger> loggers = buildLoggers(built);

        assertEquals(1, built.state().screenLoggerSpecs.size());
        assertEquals(1, built.state().fileLoggerSpecs.size());
        assertEquals(1, built.state().treeLoggerSpecs.size());
        assertEquals(3, loggers.size());

        assertEquals("test.log", fileName(loggers.get(1)));
        assertEquals("test.trees", fileName(loggers.get(2)));
        assertEquals(Logger.LOGMODE.tree, loggers.get(2).modeInput.get());
    }

    @Test
    public void testTreeLoggerWithoutTreeLogsAllLoggableTrees() {
        Built built =
                tile(
                        MODEL
                                + """
                                mcmc {
                                    Logger logger = treeLogger(logEvery=500, file="trees.trees")
                                }
                                """);

        // the tree is optional, so building must not fail and must fall back to every tree
        List<Logger> loggers = buildLoggers(built);
        Logger treeLogger = onlyTreeLogger(loggers);

        assertEquals(500, treeLogger.everyInput.get());
        assertEquals("trees.trees", fileName(treeLogger));
        assertEquals(trees(built.state()), loggables(treeLogger));
    }

    @Test
    public void testTreeLoggerWithExplicitTree() {
        Built built =
                tile(
                        MODEL
                                + """
                                mcmc {
                                    Logger logger = treeLogger(logEvery=500, file="trees.trees", tree=tree)
                                }
                                """);

        List<Logger> loggers = buildLoggers(built);
        Logger treeLogger = onlyTreeLogger(loggers);

        assertEquals(1, built.state().treeLoggerSpecs.size());
        assertEquals("trees.trees", fileName(treeLogger));
        assertEquals(trees(built.state()), loggables(treeLogger));
    }

    @Test
    public void testExplicitScreenLoggerReplacesTheDefaultOne() {
        Built built =
                tile(
                        MODEL
                                + """
                                mcmc {
                                    Logger logger = screenLogger(logEvery=7)
                                }
                                """);

        List<Logger> loggers = buildLoggers(built);

        assertEquals(1, built.state().screenLoggerSpecs.size());
        assertEquals(7, loggers.get(0).everyInput.get());

        // the file and tree loggers are still filled in with their defaults
        assertEquals(1, built.state().fileLoggerSpecs.size());
        assertEquals(1, built.state().treeLoggerSpecs.size());
    }

    @Test
    public void testScreenAndFileLoggersAlwaysLogPosteriorPriorAndLikelihood() {
        Built built = tile(MODEL);
        List<Logger> loggers = buildLoggers(built);

        for (Logger logger : List.of(loggers.get(0), loggers.get(1))) {
            List<BEASTObject> loggables = loggables(logger);

            assertSame(built.posterior(), loggables.get(0));
            assertSame(built.prior(), loggables.get(1));
            assertSame(built.likelihood(), loggables.get(2));

            // no duplicates
            assertEquals(new HashSet<>(loggables).size(), loggables.size());
        }
    }

    @Test
    public void testFileLoggerWithExplicitParameters() {
        Built built =
                tile(
                        MODEL
                                + """
                                mcmc {
                                    Logger logger = fileLogger(logEvery=100, file="out.log", parameters=[birthRate])
                                }
                                """);

        List<Logger> loggers = buildLoggers(built);
        Logger fileLogger = loggers.stream()
                .filter(l -> "out.log".equals(fileName(l)))
                .findFirst()
                .orElseThrow();

        assertEquals(100, fileLogger.everyInput.get());

        List<BEASTObject> loggables = loggables(fileLogger);
        assertEquals(4, loggables.size());
        assertEquals("birthRate", loggables.get(3).getID());
    }

    @Test
    public void testBuildLoggersIsIdempotent() {
        Built built = tile(MODEL);

        List<Logger> first = buildLoggers(built);
        List<Logger> second =
                built.state().buildLoggers(built.posterior(), built.prior(), built.likelihood());

        assertSame(first, second);
        assertEquals(1, built.state().screenLoggerSpecs.size());
        assertEquals(1, built.state().fileLoggerSpecs.size());
        assertEquals(1, built.state().treeLoggerSpecs.size());
    }

    /* helpers */

    /// Lexes, parses, tiles and applies the given source, returning the resulting state
    /// together with the posterior, prior and likelihood wired up as the runner does.
    private Built tile(String source) {
        List<Token> tokens = new Lexer(source).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();

        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        VariableResolver variableResolver = new VariableResolver(statements);

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluateTiles =
                new EvaluateTiles<>(
                        new BeastCoreTileLibrary().getTiles(),
                        new ArrayList<>(),
                        variableResolver,
                        stochasticityResolver);

        BEASTState state = new BEASTState("test");

        PrintStream original = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            List<Tile<?, BEASTState>> bestTilings = evaluateTiles.getBestTiling(statements);
            assertTrue(
                    bestTilings.stream().noneMatch(Objects::isNull),
                    "The source could not be tiled: " + source);

            for (Tile<?, BEASTState> tile : bestTilings) {
                tile.apply(state, new IdentityHashMap<>());
            }
        } finally {
            System.setOut(original);
        }

        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(state.getAvailableID("prior"));
        state.setInput(prior, prior.pDistributions, new ArrayList<>(state.priorDistributions.values()));

        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(state.getAvailableID("likelihood"));
        state.setInput(likelihood, likelihood.pDistributions, state.likelihoodDistributions);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(state.getAvailableID("posterior"));
        state.setInput(posterior, posterior.pDistributions, List.of(prior, likelihood));

        return new Built(state, posterior, prior, likelihood);
    }

    private List<Logger> buildLoggers(Built built) {
        return built.state().buildLoggers(built.posterior(), built.prior(), built.likelihood());
    }

    private Logger onlyTreeLogger(List<Logger> loggers) {
        List<Logger> treeLoggers = loggers.stream()
                .filter(l -> l.modeInput.get() == Logger.LOGMODE.tree)
                .toList();
        assertEquals(1, treeLoggers.size());
        return treeLoggers.get(0);
    }

    private String fileName(Logger logger) {
        return logger.fileNameInput.get();
    }

    @SuppressWarnings("unchecked")
    private List<BEASTObject> loggables(Logger logger) {
        return (List<BEASTObject>) (List<?>) logger.loggersInput.get();
    }

    /// Returns every tree in the state, in the order the logger selector collects them.
    private List<BEASTObject> trees(BEASTState state) {
        List<BEASTObject> trees = new ArrayList<>();
        for (BEASTObject object : state.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree) trees.add(object);
        }
        for (BEASTObject object : state.calculationNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree) trees.add(object);
        }
        return trees;
    }
}
