package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;

class CauchyLocationBindingTest {
    private static final double TOLERANCE = 1e-10;
    private static final double OBSERVED = -0.4;
    private static final double SCALE = 0.5;

    @ParameterizedTest
    @CsvSource({
        "Gamma, PositiveReal",
        "LogNormal, PositiveReal",
        "Exponential, NonNegativeReal",
        "Beta, Probability",
        "Normal, Real"
    })
    void randomLocationRemainsConnectedThroughAcceptanceAndRejection(String family, String declaredType)
            throws Exception {
        String upstreamCall =
                switch (family) {
                    case "Gamma" -> "Gamma(shape=2.0, rate=1.0)";
                    case "LogNormal" -> "LogNormal(logMean=0.3, logSd=0.6)";
                    case "Exponential" -> "Exponential(rate=2.0)";
                    case "Beta" -> "Beta(alpha=2.0, beta=2.0)";
                    case "Normal" -> "Normal(mean=-1.0, sd=1.0)";
                    default -> throw new IllegalArgumentException(family);
                };

        Real expectedDomain =
                switch (family) {
                    case "Gamma", "LogNormal" -> PositiveReal.INSTANCE;
                    case "Exponential" -> NonNegativeReal.INSTANCE;
                    case "Beta" -> UnitInterval.INSTANCE;
                    case "Normal" -> Real.INSTANCE;
                    default -> throw new IllegalArgumentException(family);
                };

        String source = declaredType + " loc ~ " + upstreamCall + "\n"
                + "Real y ~ Cauchy(location=loc, scale=0.5) observed as -0.4";

        BEASTState graph = build(source);
        RealScalarParam<?> location = parameter(graph, "loc");
        var upstreamPrior = graph.priorDistributions.get(location);
        assertNotNull(upstreamPrior);

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());

        Cauchy likelihood = assertInstanceOf(Cauchy.class, graph.likelihoodDistributions.getFirst());
        RealScalarParam<?> observation = assertInstanceOf(RealScalarParam.class, likelihood.paramInput.get());

        assertSame(location, likelihood.locationInput.get());
        assertSame(expectedDomain, location.getDomain());
        assertTrue(location.getOutputs().contains(likelihood));
        assertEquals(SCALE, likelihood.scaleInput.get().get(), 0.0);
        assertEquals(OBSERVED, observation.get(), 0.0);
        assertSame(Real.INSTANCE, observation.getDomain());
        assertFalse(graph.stateNodes.containsKey(observation));
        assertEquals(List.of(location), graph.operators.getFirst().listStateNodes());

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(upstreamPrior, likelihood));

        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        assertEquals(
                upstreamLogDensity(family, location.get()) + cauchyLogDensity(OBSERVED, location.get(), SCALE),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        // These explicit proposals test binding and state restoration.
        // They do not test whether the existing operator can cross zero.
        double[] proposedLocations =
                family.equals("Normal") ? new double[] {-1.0, 0.0, 1.0} : new double[] {0.6, 0.7, 0.8};
        boolean[] decisions = {true, false, true};

        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousLocation = location.get();
            double previousPrior = upstreamPrior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            location.set(proposedLocations[step]);
            double proposedLocation = location.get();

            assertEquals(proposedLocation, likelihood.locationInput.get().get(), 0.0);
            assertEquals(OBSERVED, observation.get(), 0.0);

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedPrior = upstreamLogDensity(family, proposedLocation);
            double expectedLikelihood = cauchyLogDensity(OBSERVED, proposedLocation, SCALE);
            double proposedPosterior = posterior.calculateLogP();

            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousLikelihood, expectedLikelihood, TOLERANCE);
            assertEquals(expectedPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedLikelihood, proposedPosterior, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            double currentLocation = accept ? proposedLocation : previousLocation;
            assertEquals(currentLocation, location.get(), 0.0);
            assertEquals(currentLocation, likelihood.locationInput.get().get(), 0.0);
            assertEquals(OBSERVED, observation.get(), 0.0);
            assertSame(location, likelihood.locationInput.get());
            assertSame(expectedDomain, location.getDomain());

            // Check restored caches before recalculating any density.
            assertEquals(accept ? expectedPrior : previousPrior, upstreamPrior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);

            // Also verify that the native distribution reads the restored value.
            assertEquals(cauchyLogDensity(OBSERVED, currentLocation, SCALE), likelihood.calculateLogP(), TOLERANCE);

            assertEquals(
                    upstreamLogDensity(family, currentLocation) + cauchyLogDensity(OBSERVED, currentLocation, SCALE),
                    posterior.calculateLogP(),
                    TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0, 1.0})
    void constantLocationsStillSupportNegativeZeroAndPositiveValues(double location) throws Exception {
        BEASTState graph = build("Real y ~ Cauchy(location=" + location + ", scale=0.5) observed as -0.4");

        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());

        Cauchy likelihood = assertInstanceOf(Cauchy.class, graph.likelihoodDistributions.getFirst());
        assertEquals(location, likelihood.locationInput.get().get(), 0.0);
        assertEquals(OBSERVED, likelihood.paramInput.get().get(), 0.0);
        assertEquals(cauchyLogDensity(OBSERVED, location, SCALE), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-1.0"})
    void coreStillRejectsNonPositiveScales(String scale) {
        assertThrows(
                TypeError.class, () -> build("Real y ~ Cauchy(location=0.0, scale=" + scale + ") observed as -0.4"));
    }

    @Test
    void shortChainEstimatesPositiveLocationForAnObservedValue(@TempDir Path directory) throws Exception {
        String source = """
                PositiveReal loc ~ Gamma(shape=2.0, rate=1.0)
                Real y ~ Cauchy(location=loc, scale=0.5) observed as -0.4

                mcmc {
                    Integer chainLength = 200
                    Integer defaultLogEvery = 10
                    Integer randomSeed = 12345
                }
                """;

        Path prefix = directory.resolve("cauchy-location");
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec(prefix.toString());

        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();

        assertEquals(22, lines.size(), "Header plus samples 0, 10, ..., 200.");
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int locationColumn = headers.indexOf("loc");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(locationColumn >= 0 && posteriorColumn >= 0, headers.toString());

        Set<Double> values = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double location = Double.parseDouble(fields[locationColumn]);
            double posterior = Double.parseDouble(fields[posteriorColumn]);

            assertTrue(Double.isFinite(location) && location > 0.0);
            assertTrue(Double.isFinite(posterior));
            values.add(location);
        }

        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(values.size() > 1, "The existing operator must update loc.");
    }

    private static double cauchyLogDensity(double value, double location, double scale) {
        double z = (value - location) / scale;
        return -Math.log(Math.PI) - Math.log(scale) - Math.log1p(z * z);
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double z = (value - mean) / sd;
        return -Math.log(sd) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * z * z;
    }

    private static double upstreamLogDensity(String family, double value) {
        return switch (family) {
            case "Gamma" -> Math.log(value) - value;
            case "LogNormal" -> normalLogDensity(Math.log(value), 0.3, 0.6) - Math.log(value);
            case "Exponential" -> Math.log(2.0) - 2.0 * value;
            case "Beta" -> Math.log(6.0) + Math.log(value) + Math.log1p(-value);
            case "Normal" -> normalLogDensity(value, -1.0, 1.0);
            default -> throw new IllegalArgumentException(family);
        };
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
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

        var graph = evaluator.applyBestTiling(new BEASTState("cauchy-location-binding"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
