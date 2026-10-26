package tiles.observations;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.distributions.DiscreteUniformTile;
import tiles.distributions.LogNormalTile;
import tiles.distributions.NormalTile;
import tiles.distributions.PoissonTile;
import tiles.distributions.UniformTile;
import tiles.misc.LiteralTile;

class ObservedAsTileTest {
    private record ModelCase(
            String source,
            Supplier<GeneratorTile<?, BEASTState>> generator,
            Class<?> parameterClass,
            Class<?> domain) {}

    private static final ModelCase DISCRETE = new ModelCase(
            "Integer x ~ DiscreteUniform(lower=1, upper=10) observed as 9",
            DiscreteUniformTile::new,
            IntScalarParam.class,
            Int.class);

    private static Stream<ModelCase> observedModels() {
        return Stream.of(
                DISCRETE,
                new ModelCase(
                        "NonNegativeInteger x ~ Poisson(rate=4.0) observed as 9",
                        PoissonTile::new,
                        IntScalarParam.class,
                        NonNegativeInt.class),
                new ModelCase(
                        "Real x ~ Uniform(lower=1.0, upper=10.0) observed as 9",
                        UniformTile::new,
                        RealScalarParam.class,
                        Real.class),
                new ModelCase(
                        "Real x ~ Normal(mean=1.0, sd=2.0) observed as 9",
                        NormalTile::new,
                        RealScalarParam.class,
                        Real.class),
                new ModelCase(
                        "PositiveReal x ~ LogNormal(logMean=1.0, logSd=0.5) observed as 9",
                        LogNormalTile::new,
                        RealScalarParam.class,
                        PositiveReal.class));
    }

    @ParameterizedTest
    @MethodSource("observedModels")
    void everyReturnedCandidateAppliesACompatibleObservedParameter(ModelCase model) throws Throwable {
        Fixture fixture = fixture(model);
        Set<Tile<?, BEASTState>> literalCandidates = fixture.inputs().get(fixture.observed().observedAs);
        assertTrue(literalCandidates.stream()
                .anyMatch(tile -> new TypeToken<IntScalarParam<Int>>() {}.equals(tile.getTypeToken())));
        assertTrue(literalCandidates.stream()
                .anyMatch(tile -> new TypeToken<RealScalarParam<Real>>() {}.equals(tile.getTypeToken())));

        Set<Tile<?, BEASTState>> candidates = candidates(fixture);
        assertFalse(candidates.isEmpty());
        // Test every possible candidate, not whichever HashSet iteration or tie-breaking picks.
        assertAll(candidates.stream().map(candidate -> () -> {
            // Tiles memoize their application. Rebuild the equivalent candidate so shared
            // distribution tiles are not consumed twice across separate test graphs.
            Fixture fresh = fixture(model);
            TypeToken<?> observedType = candidate.getTypeToken();
            Tile<?, BEASTState> observation = fresh.inputs().get(fresh.observed().observedAs).stream()
                    .filter(tile -> tile.getTypeToken().equals(observedType))
                    .findFirst()
                    .orElseThrow();
            fresh.inputs().put(fresh.observed().observedAs, Set.of(observation));
            Set<Tile<?, BEASTState>> rebuilt = candidates(fresh);
            assertEquals(1, rebuilt.size());
            BEASTState graph = new BEASTState("observed-candidate");
            Object actual = rebuilt.iterator().next().apply(graph, new IdentityHashMap<>());
            assertTrue(model.parameterClass().isInstance(actual), "Incorrect observed parameter class: " + actual);
            Object domain = actual instanceof IntScalarParam<?> integer
                    ? integer.getDomain()
                    : ((RealScalarParam<?>) actual).getDomain();
            assertTrue(model.domain().isInstance(domain), "Incompatible observed numeric domain: " + domain);
            assertEquals(
                    9.0,
                    actual instanceof IntScalarParam<?> integer ? integer.get() : ((RealScalarParam<?>) actual).get());
            graph.initializeBEASTObjects();
            assertTrue(graph.stateNodes.isEmpty());
            assertTrue(graph.operators.isEmpty());
            assertTrue(graph.priorDistributions.isEmpty());
            assertEquals(1, graph.likelihoodDistributions.size());
            assertTrue(Double.isFinite(graph.likelihoodDistributions.getFirst().calculateLogP()));
        }));
    }

    @Test
    void rejectsWrongScalarShapeBeforeBuildingAnyEngineObjects() throws Throwable {
        Fixture fixture = fixture(DISCRETE);
        keepObservationType(fixture, new TypeToken<RealScalarParam<Real>>() {});
        assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> candidates(fixture));
    }

    @Test
    void rejectsBroaderIntegerDomainForPoisson() throws Throwable {
        Fixture fixture = fixture(new ModelCase(
                "NonNegativeInteger x ~ Poisson(rate=4.0) observed as 9",
                PoissonTile::new,
                IntScalarParam.class,
                NonNegativeInt.class));
        keepObservationType(fixture, new TypeToken<IntScalarParam<Int>>() {});
        assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> candidates(fixture));
    }

    @Test
    void rejectsBroaderRealDomainForLogNormal() throws Throwable {
        Fixture fixture = fixture(new ModelCase(
                "PositiveReal x ~ LogNormal(logMean=1.0, logSd=0.5) observed as 9",
                LogNormalTile::new,
                RealScalarParam.class,
                PositiveReal.class));
        keepObservationType(fixture, new TypeToken<RealScalarParam<Real>>() {});
        assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> candidates(fixture));
    }

    @Test
    void namedPositiveRealObservationRemainsCompatibleWithRealDistribution() throws Exception {
        String source = """
                Real logValue = 0.0
                PositiveReal v = exp(logValue)
                Real x ~ Uniform(lower=0.0, upper=10.0) observed as v
                """;
        var statements = parse(source);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("named-observation"));
        graph.initializeBEASTObjects();
        var likelihood = assertInstanceOf(
                beast.base.spec.inference.distribution.Uniform.class, graph.likelihoodDistributions.getFirst());
        assertEquals(1.0, likelihood.paramInput.get().get());
        assertInstanceOf(PositiveReal.class, likelihood.paramInput.get().getDomain());
        assertEquals(-Math.log(10.0), likelihood.calculateLogP(), 1e-12);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static void keepObservationType(Fixture fixture, TypeToken<?> type) {
        Tile<?, BEASTState> observation = fixture.inputs().get(fixture.observed().observedAs).stream()
                .filter(tile -> tile.getTypeToken().equals(type))
                .findFirst()
                .orElseThrow();
        fixture.inputs().put(fixture.observed().observedAs, Set.of(observation));
    }

    private static Set<Tile<?, BEASTState>> candidates(Fixture fixture) throws FailedTilingAttempt {
        return new ObservedAsTile()
                .tryToTile(fixture.observed(), fixture.inputs(), fixture.variables(), fixture.stochasticity());
    }

    private record Fixture(
            Stmt.ObservedAs observed,
            Map<AstNode, Set<Tile<?, BEASTState>>> inputs,
            VariableResolver variables,
            StochasticityResolver stochasticity) {}

    private static Fixture fixture(ModelCase model) throws FailedTilingAttempt {
        List<Stmt> statements = parse(model.source());
        Stmt.ObservedAs observed = assertInstanceOf(Stmt.ObservedAs.class, statements.getFirst());
        Expr.Call call = assertInstanceOf(Expr.Call.class, assertInstanceOf(Stmt.Draw.class, observed.stmt).expression);
        var variables = new VariableResolver(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        Map<AstNode, Set<Tile<?, BEASTState>>> inputs = new HashMap<>();
        for (Expr.Argument argument : call.arguments) {
            Expr.Literal literal = assertInstanceOf(Expr.Literal.class, argument.expression);
            inputs.put(argument, new LiteralTile<>().tryToTile(literal, Map.of(), variables, stochasticity));
        }
        inputs.put(call, model.generator().get().tryToTile(call, inputs, variables, stochasticity));
        inputs.put(
                observed.observedAs,
                new LiteralTile<>().tryToTile(observed.observedAs, Map.of(), variables, stochasticity));
        return new Fixture(observed, inputs, variables, stochasticity);
    }

    private static List<Stmt> parse(String source) {
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
    }
}
