package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.LogisticGrowth;

public class LogisticGrowthEndToEndTest {

    @Test
    public void buildsPopFuncModelFromPhyloSpec() throws IOException {
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    carryingCapacity=1000.0,
                    growthRate=0.25
                )
                """;

        List<Stmt> statements = parse(source);

        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> availableTiles =
                TileLibrary.loadSelected(BEASTState.class, List.of("popfunc", "beast2"));
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(availableTiles, variableResolver, stochasticityResolver);

        List<Tile<?, BEASTState>> selectedTiles = evaluator.getBestTiling(statements);
        assertEquals(1, selectedTiles.size());

        Object result = selectedTiles
                .getFirst()
                .apply(new BEASTState("popfunc-logistic-e2e"), new IdentityHashMap<>());

        LogisticGrowth logistic = assertInstanceOf(LogisticGrowth.class, result);
        assertEquals(5.0, logistic.getT50());
        assertEquals(1000.0, logistic.getNCarryingCapacity());
        assertEquals(0.25, logistic.getGrowthRateB());
        assertEquals(0.0, logistic.getRawNA());
    }

    @Test
    public void buildsOptionalAncestralPopulationFromPhyloSpec() throws IOException {
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    carryingCapacity=1000.0,
                    growthRate=0.25,
                    ancestralPopulationSize=100.0
                )
                """;

        List<Stmt> statements = parse(source);

        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> availableTiles =
                TileLibrary.loadSelected(BEASTState.class, List.of("popfunc", "beast2"));
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(availableTiles, variableResolver, stochasticityResolver);

        Object result = evaluator.getBestTiling(statements)
                .getFirst()
                .apply(new BEASTState("popfunc-logistic-ancestral"), new IdentityHashMap<>());

        LogisticGrowth logistic = assertInstanceOf(LogisticGrowth.class, result);
        assertEquals(100.0, logistic.getRawNA());
        assertEquals(100.0, logistic.getEffectiveNA());
    }

    @Test
    public void rejectsNegativeGrowthRateBeforeTiling() throws IOException {
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    carryingCapacity=1000.0,
                    growthRate=-0.25
                )
                """;

        List<Stmt> statements = parse(source);
        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));

        TypeError error = assertThrows(TypeError.class, () -> typeResolver.visitStatements(statements));
        assertEquals(
                "Wrong argument type for function `logisticPopulationFunction` and argument `growthRate`. "
                        + "You need to use a value of type 'NonNegativeReal'.",
                error.getMessage());
    }

    @Test
    public void rejectsNegativeAncestralPopulationBeforeTiling() throws IOException {
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    carryingCapacity=1000.0,
                    growthRate=0.25,
                    ancestralPopulationSize=-100.0
                )
                """;

        List<Stmt> statements = parse(source);
        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));

        TypeError error = assertThrows(TypeError.class, () -> typeResolver.visitStatements(statements));
        assertEquals(
                "Wrong argument type for function `logisticPopulationFunction` and argument "
                        + "`ancestralPopulationSize`. You need to use a value of type 'NonNegativeReal'.",
                error.getMessage());
    }

    @Test
    public void reportsMissingCarryingCapacityBeforeTiling() throws IOException {
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    growthRate=0.25
                )
                """;

        List<Stmt> statements = parse(source);
        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));

        TypeError error = assertThrows(TypeError.class, () -> typeResolver.visitStatements(statements));
        assertEquals(
                "Function `logisticPopulationFunction` takes the required argument `carryingCapacity`.",
                error.getMessage());
    }

    private static List<Stmt> parse(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        return new EvaluateLiterals().transform(statements);
    }
}
