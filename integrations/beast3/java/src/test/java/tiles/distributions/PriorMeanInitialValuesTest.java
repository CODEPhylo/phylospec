package tiles.distributions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.inference.StateNode;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
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

/** Beta and Dirichlet parameters start at their prior means rather than at fixed values. */
class PriorMeanInitialValuesTest {

    @Test
    void betaStartsAtItsPriorMean() throws Exception {
        BEASTState state = evaluate("Probability epsilon ~ Beta(alpha=1.0, beta=30.0)");

        RealScalarParam<?> epsilon = stateNode(state, "epsilon", RealScalarParam.class);
        assertEquals(1.0 / 31.0, epsilon.get(), 1e-15);
        assertTrue(Double.isFinite(state.priorDistributions.get(epsilon).calculateLogP()));
    }

    @Test
    void betaReadsTheStartingValueOfASampledShape() throws Exception {
        BEASTState state = evaluate("""
                PositiveReal a ~ LogNormal(logMean=0.0, logSd=1.0)
                Probability p ~ Beta(alpha=a, beta=3.0)
                """);

        // a starts at its prior median 1.0, so p starts at 1 / (1 + 3)
        assertEquals(0.25, stateNode(state, "p", RealScalarParam.class).get(), 1e-15);
    }

    @Test
    void betaFallsBackToOneHalfWhenTheMeanIsNotRepresentable() {
        assertEquals(0.5, BetaTile.initialValue(Double.MAX_VALUE, Double.MAX_VALUE));
    }

    @Test
    void dirichletStartsAtItsPriorMean() throws Exception {
        BEASTState state = evaluate("Simplex rates ~ Dirichlet(concentration=[2.0, 20.0, 2.0, 2.0, 20.0, 2.0])");

        SimplexParam rates = stateNode(state, "rates", SimplexParam.class);
        double[] mean = {2.0 / 48, 20.0 / 48, 2.0 / 48, 2.0 / 48, 20.0 / 48, 2.0 / 48};
        assertArrayEquals(mean, rates.getValues(), 1e-15);
        assertTrue(Double.isFinite(state.priorDistributions.get(rates).calculateLogP()));
    }

    private static <T extends StateNode> T stateNode(BEASTState state, String id, Class<T> type) {
        return type.cast(state.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no state node " + id + " in " + state.stateNodes.keySet())));
    }

    private static BEASTState evaluate(String source) throws Exception {
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
        BEASTState state = evaluator.applyBestTiling(new BEASTState("test"));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
