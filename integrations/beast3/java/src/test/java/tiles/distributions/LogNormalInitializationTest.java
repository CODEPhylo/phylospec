package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class LogNormalInitializationTest {
    private static Stream<Arguments> representativePriors() {
        return Stream.of(
                Arguments.of("logMean=5.0, logSd=0.5", 5.0, 0.5, false),
                Arguments.of("logMean=-0.95, logSd=0.2", -0.95, 0.2, false),
                Arguments.of("mean=2.0, logSd=0.5", 2.0, 0.5, true),
                Arguments.of("mean=1.0, logSd=2.0", 1.0, 2.0, true));
    }

    @ParameterizedTest
    @MethodSource("representativePriors")
    void startsAtTheMedianWithoutChangingThePrior(
            String arguments, double mean, double logSd, boolean realSpace) throws Exception {
        BEASTState graph = build("PositiveReal x ~ LogNormal(" + arguments + ")");
        RealScalarParam<?> value = parameter(graph, "x");
        LogNormal prior = prior(graph, value);
        double logMedian = realSpace ? Math.log(mean) - 0.5 * logSd * logSd : mean;

        assertEquals(Math.exp(logMedian), value.get(), 1e-12);
        assertSame(PositiveReal.INSTANCE, value.getDomain());
        assertSame(value, prior.paramInput.get());
        assertEquals(mean, prior.MParameterInput.get().get());
        assertEquals(logSd, prior.SParameterInput.get().get());
        assertEquals(realSpace, prior.hasMeanInRealSpaceInput.get());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.operators.stream().anyMatch(operator -> operator.listStateNodes().contains(value)));
        assertDensity(prior, value.get(), logMedian, logSd);

        // The initial value is just a starting point: the same original prior applies elsewhere.
        value.set(value.get() * 1.5);
        assertDensity(prior, value.get(), logMedian, logSd);
    }

    @Test
    void equivalentParameterizationsHaveTheSameInitialValueAndDensity() throws Exception {
        BEASTState graph = build("""
                PositiveReal logSpace ~ LogNormal(logMean=0.0, logSd=1.0)
                PositiveReal realSpace ~ LogNormal(mean=1.6487212707001282, logSd=1.0)
                """);
        RealScalarParam<?> logSpace = parameter(graph, "logSpace");
        RealScalarParam<?> realSpace = parameter(graph, "realSpace");
        assertEquals(1.0, logSpace.get(), 1e-12);
        assertEquals(logSpace.get(), realSpace.get(), 1e-12);
        assertEquals(prior(graph, logSpace).calculateLogP(), prior(graph, realSpace).calculateLogP(), 1e-12);
    }

    @Test
    void usesTheCurrentStochasticMeanOnceWhilePreservingItsPriorConnection() throws Exception {
        String source = """
                Real mean ~ Normal(mean=3.0, sd=1.0)
                PositiveReal x ~ LogNormal(logMean=mean, logSd=0.5)
                """;
        BEASTState graph = build(source);
        RealScalarParam<?> mean = parameter(graph, "mean");
        RealScalarParam<?> value = parameter(graph, "x");
        LogNormal prior = prior(graph, value);
        double initialMean = 0.1;
        double logMedian = initialMean;
        assertEquals(initialMean, mean.get(), 1e-12);
        assertEquals(Math.exp(logMedian), value.get(), 1e-12);
        assertSame(mean, prior.MParameterInput.get());
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertDensity(prior, value.get(), logMedian, 0.5);

        double initialValue = value.get();
        double initialDensity = prior.calculateLogP();
        mean.set(initialMean + 1.0);
        assertEquals(initialValue, value.get(), "A stochastic value must not become a deterministic function of its mean.");
        assertDensity(prior, value.get(), mean.get(), 0.5);
        assertNotEquals(initialDensity, prior.calculateLogP(), 1e-12);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1000.0, 1000.0})
    void nonrepresentableMediansStillProduceFinitePositiveStartingValues(double logMean) throws Exception {
        BEASTState graph = build("PositiveReal x ~ LogNormal(logMean=" + logMean + ", logSd=1.0)");
        RealScalarParam<?> value = parameter(graph, "x");
        assertTrue(Double.isFinite(value.get()));
        assertTrue(value.get() > 0.0);
        assertEquals(logMean < 0.0 ? Double.MIN_VALUE : Double.MAX_VALUE, value.get());
        assertEquals(logMean, prior(graph, value).MParameterInput.get().get());
        assertDensity(prior(graph, value), value.get(), logMean, 1.0);
    }

    @Test
    void realSpaceMedianUnderflowDoesNotChangeTheDistributionParameters() throws Exception {
        BEASTState graph = build("PositiveReal x ~ LogNormal(mean=1.0, logSd=100.0)");
        RealScalarParam<?> value = parameter(graph, "x");
        LogNormal prior = prior(graph, value);
        assertEquals(Double.MIN_VALUE, value.get());
        assertEquals(1.0, prior.MParameterInput.get().get());
        assertEquals(100.0, prior.SParameterInput.get().get());
        assertTrue(prior.hasMeanInRealSpaceInput.get());
        assertDensity(prior, value.get(), -5000.0, 100.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1000.0, 5.0, 1000.0})
    void observedValuesAreNotReplacedByTheDefaultInitialization(double logMean) throws Exception {
        BEASTState graph = build(
                "PositiveReal x ~ LogNormal(logMean=" + logMean + ", logSd=1.0) observed as 2.0");
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        LogNormal likelihood = assertInstanceOf(LogNormal.class, graph.likelihoodDistributions.getFirst());
        assertEquals(2.0, likelihood.paramInput.get().get());
        assertEquals(logMean, likelihood.MParameterInput.get().get());
        assertDensity(likelihood, 2.0, logMean, 1.0);
    }

    private static void assertDensity(LogNormal distribution, double value, double logMean, double logSd) {
        double standardized = (Math.log(value) - logMean) / logSd;
        double expected = -Math.log(value) - Math.log(logSd) - 0.5 * Math.log(2.0 * Math.PI)
                - 0.5 * standardized * standardized;
        double actual = distribution.calculateLogP();
        assertTrue(Double.isFinite(actual));
        assertEquals(expected, actual, Math.max(1e-12, Math.abs(expected) * 1e-12));
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(RealScalarParam.class, graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID())).findFirst().orElseThrow());
    }

    private static LogNormal prior(BEASTState graph, RealScalarParam<?> value) {
        return assertInstanceOf(LogNormal.class, graph.priorDistributions.get(value));
    }

    private static BEASTState build(String source) throws Exception {
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
        var graph = evaluator.applyBestTiling(new BEASTState("lognormal-initialization"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
