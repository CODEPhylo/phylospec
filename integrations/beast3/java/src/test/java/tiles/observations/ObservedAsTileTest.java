package tiles.observations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.templatematching.AstTemplateMatcher;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;
import tiling.BoundDistribution;
import tiling.UnboundDistribution;

class ObservedAsTileTest {
    private static final TypeToken<?> INTEGER = new TypeToken<IntScalarParam<Int>>() {};
    private static final TypeToken<?> POSITIVE_INTEGER = new TypeToken<IntScalarParam<PositiveInt>>() {};
    private static final TypeToken<?> REAL = new TypeToken<RealScalarParam<Real>>() {};

    @Test
    void integerDistributionRejectsRealAndDifferentDomainCandidates() throws FailedTilingAttempt {
        var candidates = candidates(
                new TypeToken<BoundDistribution<IntScalarParam<PositiveInt>, IntUniform>>() {},
                REAL,
                INTEGER,
                POSITIVE_INTEGER);
        assertEquals(1, candidates.size());
        assertEquals(POSITIVE_INTEGER, candidates.iterator().next().getTypeToken());
    }

    @Test
    void realDistributionRejectsIntegerCandidate() throws FailedTilingAttempt {
        var candidates =
                candidates(new TypeToken<BoundDistribution<RealScalarParam<Real>, Normal>>() {}, INTEGER, REAL);
        assertEquals(1, candidates.size());
        assertEquals(REAL, candidates.iterator().next().getTypeToken());
    }

    @Test
    void unboundDistributionUsesTheSameValueContract() throws FailedTilingAttempt {
        var candidates =
                candidates(new TypeToken<UnboundDistribution<IntScalarParam<Int>, IntUniform>>() {}, REAL, INTEGER);
        assertEquals(1, candidates.size());
        assertEquals(INTEGER, candidates.iterator().next().getTypeToken());
    }

    @Test
    void incompatibleObservationFailsDuringTiling() {
        var error = assertThrows(
                FailedTilingAttempt.RejectedBoundary.class,
                () -> candidates(new TypeToken<BoundDistribution<IntScalarParam<Int>, IntUniform>>() {}, REAL));
        assertTrue(error.getReason().contains("distribution's value type"));
    }

    @Test
    void integerObservationAddsOnlyItsLikelihood() {
        assertObservedDensity("Integer value ~ DiscreteUniform(lower=2,upper=4) observed as 3", -Math.log(3));
    }

    @Test
    void realObservationAcceptsAnIntegerLiteralAsARealParameter() {
        assertObservedDensity("Real value ~ Normal(mean=3.0,sd=1.0) observed as 3", -0.5 * Math.log(2 * Math.PI));
    }

    private static void assertObservedDensity(String source, double expected) {
        var statements = new EvaluateLiterals().transform(new Parser(new Lexer(source).scanTokens()).parse());
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<BEASTState>(
                new BeastCoreTileLibrary().getTiles(), new VariableResolver(statements), stochasticity);
        var state = new BEASTState("observation");
        for (var tile : evaluator.getBestTiling(statements)) tile.apply(state, new IdentityHashMap<>());
        state.initializeBEASTObjects();
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.operators.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertEquals(1, state.likelihoodDistributions.size());
        assertEquals(expected, state.likelihoodDistributions.getFirst().calculateLogP(), 1e-12);
    }

    private static Set<Tile<?, BEASTState>> candidates(TypeToken<?> distributionType, TypeToken<?>... valueTypes)
            throws FailedTilingAttempt {
        var statements = new EvaluateLiterals()
                .transform(new Parser(new Lexer("Integer value ~ DiscreteUniform(lower=2, upper=4) observed as 3")
                                .scanTokens())
                        .parse());
        var variables = new VariableResolver(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var root = statements.getFirst();
        var nodes = new AstTemplateMatcher("Any x ~ $distribution observed as $observation").match(root, variables);
        var inputs = new IdentityHashMap<AstNode, Set<Tile<?, BEASTState>>>();
        AstNode distribution = nodes.get("$distribution");
        AstNode observation = nodes.get("$observation");
        inputs.put(distribution, Set.of(stub(distribution, distributionType)));
        Set<Tile<?, BEASTState>> values = new HashSet<>();
        for (TypeToken<?> valueType : valueTypes) values.add(stub(observation, valueType));
        inputs.put(observation, values);
        return new ObservedAsTile().tryToTile(root, inputs, variables, stochasticity);
    }

    private static Tile<?, BEASTState> stub(AstNode node, TypeToken<?> type) {
        Tile<?, BEASTState> tile = new Tile<Object, BEASTState>() {
            @Override
            public TypeToken<?> getTypeToken() {
                return type;
            }

            @Override
            public Object applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
                throw new AssertionError("Type compatibility must be checked before applying tiles.");
            }
        };
        tile.setRootNode(node);
        return tile;
    }
}
