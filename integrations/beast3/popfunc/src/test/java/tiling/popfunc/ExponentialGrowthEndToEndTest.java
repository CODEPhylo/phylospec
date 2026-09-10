package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beastconfig.BEASTState;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.ExponentialGrowth;

public class ExponentialGrowthEndToEndTest {

    @Test
    public void extendedArgumentsSelectPopFuncMapping() throws IOException {
        String source = """
                PopulationFunction population = exponentialPopulationFunction(
                    populationSize=1000.0,
                    growthRate=0.25,
                    ancestralPopulationSize=100.0
                )
                """;

        List<Stmt> statements =
                new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        TypeResolver typeResolver =
                new TypeResolver(
                        new ComponentResolver(
                                ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        VariableResolver variableResolver =
                new VariableResolver(statements);
        StochasticityResolver stochasticityResolver =
                new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> candidates =
                TileLibrary.loadSelected(
                        BEASTState.class,
                        List.of("beast2", "popfunc"));
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(
                        candidates,
                        variableResolver,
                        stochasticityResolver);

        Object result =
                evaluator.getBestTiling(statements)
                        .getFirst()
                        .apply(
                                new BEASTState("popfunc-exponential-e2e"),
                                new IdentityHashMap<>());

        ExponentialGrowth exponential =
                assertInstanceOf(ExponentialGrowth.class, result);
        assertEquals(1_000.0, exponential.getN0());
        assertEquals(0.25, exponential.getGrowthRate());
        assertEquals(100.0, exponential.getNA());
    }
}
