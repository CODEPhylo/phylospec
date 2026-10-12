package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;

class PositiveIIDTileTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(
            strings = {"LogNormal(mean=1.0,logSd=0.5)", "LogNormal(logMean=0.0,logSd=0.5)", "Gamma(shape=2.0,rate=3.0)"
            })
    void registersOnlyVectorPriorAndPreservesScalarLaw(String law) throws Exception {
        BEASTState state = evaluate("Vector<PositiveReal> rates ~ IID(base=" + law + ",num=3)");
        assertEquals(1, state.stateNodes.size());
        assertEquals(1, state.priorDistributions.size());
        RealVectorParam<?> rates = rates(state);
        assertEquals(3, rates.size());
        IID<?, ?, ?> iid = assertInstanceOf(IID.class, state.priorDistributions.get(rates));
        assertSame(rates, iid.paramInput.get());
        assertNull(iid.distInput.get().paramInput.get(), "The base law must not acquire a separate scalar draw.");
        for (int i = 0; i < rates.size(); i++) {
            rates.set(i, 0.2 + i * 0.5);
        }
        double expected = 0.0;
        for (double value : rates.getElements()) {
            expected += iid.distInput.get().logDensity(value);
        }
        assertEquals(expected, iid.calculateLogP(), 1e-12);
        assertFalse(state.operators.isEmpty());
        assertTrue(state.operators.stream()
                .allMatch(operator -> operator.listStateNodes().equals(List.of(rates))));
    }

    @Test
    void tracksHyperparameterUpdatesAndRejectedProposals() throws Exception {
        BEASTState state = evaluate("""
                PositiveReal sigma ~ LogNormal(logMean=0.0,logSd=1.0)
                Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=sigma),num=3)
                """);
        var rates = rates(state);
        RealScalarParam<?> sigma = (RealScalarParam<?>) state.stateNodes.keySet().stream()
                .filter(node -> "sigma".equals(node.getID()))
                .findFirst()
                .orElseThrow();
        var iid = (IID<?, ?, ?>) state.priorDistributions.get(rates);
        var law = (LogNormal) iid.distInput.get();
        assertSame(sigma, law.SParameterInput.get());
        assertTrue(sigma.getOutputs().contains(law));
        assertTrue(law.getOutputs().contains(iid));
        assertEquals(2, state.priorDistributions.size());

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", new ArrayList<>(state.priorDistributions.values()));
        State nativeState = new State();
        nativeState.initByName("stateNode", new ArrayList<>(state.stateNodes.keySet()));
        nativeState.initialise();
        nativeState.setPosterior(posterior);
        double original = nativeState.robustlyCalcPosterior(posterior);
        double initialSigma = sigma.get();
        double initialRate = rates.get(0);
        double oldPrior = iid.getCurrentLogP();

        nativeState.store(1);
        sigma.set(0.8);
        rates.set(0, 1.7);
        nativeState.storeCalculationNodes();
        nativeState.checkCalculationNodesDirtiness();
        double proposed = posterior.calculateLogP();
        assertTrue(Double.isFinite(proposed));
        assertNotEquals(original, proposed, 1e-10);
        double expected = 0.0;
        for (double value : rates.getElements()) {
            expected += law.logDensity(value);
        }
        assertEquals(expected, iid.getCurrentLogP(), 1e-12);
        assertNotEquals(oldPrior, iid.getCurrentLogP(), 1e-10);

        nativeState.restore();
        nativeState.restoreCalculationNodes();
        nativeState.setEverythingDirty(false);
        assertEquals(initialSigma, sigma.get());
        assertEquals(initialRate, rates.get(0));
        assertEquals(original, nativeState.robustlyCalcPosterior(posterior), 1e-12);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void supportsFixedDimensions(int size) throws Exception {
        assertEquals(
                size,
                rates(evaluate("Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=" + size + ")"))
                        .size());
    }

    @Test
    void supportsBranchCountOfFixedTree() throws Exception {
        var state = evaluate("""
                Tree tree = fromNewick("((A:1,B:1):1,(C:1,D:1):1);")
                Vector<Rate> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=numBranches(tree))
                """);
        assertEquals(6, rates(state).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "true"})
    void rejectsInvalidDimensions(String size) {
        assertThrows(
                TypeError.class,
                () -> evaluate(
                        "Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=" + size + ")"));
    }

    @Test
    void rejectsSampledDimension() {
        var statements = new Parser(
                        new Lexer("Integer n ~ DiscreteUniform(lower=1,upper=4)\nInteger size = n").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new PositiveIIDTile()
                .numInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("num"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void doesNotNarrowSignedNormalToPositiveRates() {
        assertThrows(
                TileApplicationError.class,
                () -> evaluate("Vector<Real> values ~ IID(base=Normal(mean=0.0,sd=1.0),num=3)"));
    }

    @Test
    void existingRelaxedClockRemainsDiscretized() throws Exception {
        BEASTState state = evaluate("""
                Tree tree = fromNewick("((A:1,B:1):1,(C:1,D:1):1);")
                Vector<Rate> branchRates ~ RelaxedClock(base=LogNormal(mean=1.0,logSd=0.5),clockRate=1.0,tree=tree)
                """);
        assertTrue(state.stateNodes.keySet().stream().anyMatch(IntVectorParam.class::isInstance));
        assertFalse(state.stateNodes.keySet().stream().anyMatch(RealVectorParam.class::isInstance));
    }

    @Test
    void runsVectorPriorAndUpdatesRates() throws Exception {
        String source = """
                Vector<PositiveReal> rates ~ IID(base=LogNormal(mean=1.0,logSd=0.5),num=3)
                mcmc {
                  Integer chainLength=200
                  Integer defaultLogEvery=10
                  Integer randomSeed=12345
                }
                """;
        new PhyloSpecRunner(source, List.of("beast2"))
                .runPhyloSpec(directory.resolve("iid").toString());
        var rows = Files.readAllLines(directory.resolve("iid.log")).stream()
                .filter(line -> line.matches("[0-9]+\\s+.*"))
                .map(line -> line.split("\\s+"))
                .toList();
        assertEquals(21, rows.size());
        assertEquals("200", rows.getLast()[0]);
        assertTrue(rows.stream().map(row -> row[row.length - 1]).distinct().count() > 1);
        for (var row : rows) {
            for (int i = 1; i < row.length; i++) {
                assertTrue(Double.isFinite(Double.parseDouble(row[i])));
            }
        }
    }

    private RealVectorParam<?> rates(BEASTState state) {
        return (RealVectorParam<?>) state.stateNodes.keySet().stream()
                .filter(node -> "rates".equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private BEASTState evaluate(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState state = evaluator.applyBestTiling(
                new BEASTState(directory.resolve("test").toString(), directory));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
