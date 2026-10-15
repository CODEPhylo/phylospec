package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beastconfig.BEASTState;
import adapters.popfunc.ValidatedConsExpCons;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.Cons_Exp_ConsGrowth;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;
import tiles.popfunc.ConsExpConsGeneratedTile;
import tiles.popfunc.GompertzF0GeneratedTile;
import tiles.popfunc.GompertzT50GeneratedTile;
import tiles.popfunc.PopFuncTileLibrary;

public class PackageModelsEndToEndTest {

    @Test
    public void registersAllPopFuncGenerators() {
        List<CandidateTile<BEASTState>> tiles = new PopFuncTileLibrary().getTiles();

        assertEquals(9, tiles.size());
        assertTrue(tiles.stream().anyMatch(ConsExpConsGeneratedTile.class::isInstance));
        assertTrue(tiles.stream().anyMatch(GompertzF0GeneratedTile.class::isInstance));
        assertTrue(tiles.stream().anyMatch(GompertzT50GeneratedTile.class::isInstance));
    }

    @Test
    public void buildsConstantExponentialConstantModel() throws IOException {
        Object result = build("""
                use popfunc.functions.coalescent

                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0,
                    growthRate=0.2,
                    growthStartAge=2.0,
                    growthEndAge=10.0
                )
                """);

        Cons_Exp_ConsGrowth model = assertInstanceOf(Cons_Exp_ConsGrowth.class, result);
        assertEquals(1000.0, model.NCInput.get().get());
        assertEquals(0.2, model.rInput.get().get());
        assertEquals(2.0, model.xInput.get().get());
        assertEquals(10.0, model.tauInput.get().get());
    }

    @Test
    public void rejectsReversedAgesBeforeCallingTheNativeInitializer() throws IOException {
        var model = assertInstanceOf(ValidatedConsExpCons.class, build("""
                use popfunc.functions.coalescent
                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0, growthRate=0.2, growthStartAge=2.0, growthEndAge=1.0)
                """));
        var error = assertThrows(IllegalArgumentException.class, model::initAndValidate);
        assertTrue(error.getMessage().contains("growthStartAge <= growthEndAge"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"recentPopulationSize", "growthRate"})
    public void rejectsOverflowedPositiveInputsBeforeCallingTheNativeInitializer(String argument) throws IOException {
        // A floating-point literal can overflow to +Infinity while still resolving as PositiveReal.
        String source = """
                use popfunc.functions.coalescent
                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0, growthRate=0.2, growthStartAge=2.0, growthEndAge=10.0)
                """.replaceAll(argument + "=[0-9.]+", argument + "=" + "9".repeat(400) + ".0");
        var model = assertInstanceOf(ValidatedConsExpCons.class, build(source));
        var error = assertThrows(IllegalArgumentException.class, model::initAndValidate);
        assertTrue(error.getMessage().contains("finite, positive " + argument), error.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"0.0, 0.0", "2.0, 2.0", "2.0, 10.0"})
    public void fixedPopulationCalculationsStillComeFromTheNativeModel(double start, double end)
            throws IOException {
        var model = assertInstanceOf(ValidatedConsExpCons.class, build("""
                use popfunc.functions.coalescent
                PopulationFunction population = constantExponentialConstantPopulationFunction(
                    recentPopulationSize=1000.0, growthRate=0.2, growthStartAge=%s, growthEndAge=%s)
                """.formatted(start, end)));
        model.initAndValidate();
        var reference = new Cons_Exp_ConsGrowth();
        reference.initByName("NC", model.NCInput.get(), "r", model.rInput.get(),
                "x", model.xInput.get(), "tau", model.tauInput.get());
        for (double time : new double[]{0.0, start, end, 20.0}) {
            assertEquals(reference.getPopSize(time), model.getPopSize(time));
            assertEquals(reference.getIntensity(time), model.getIntensity(time));
        }
    }

    @Test
    public void buildsGompertzF0ModelWithAncestralSize() throws IOException {
        Object result = build("""
                use popfunc.functions.coalescent

                PopulationFunction population = gompertzF0PopulationFunction(
                    initialProportion=0.2,
                    growthRate=0.3,
                    initialPopulationSize=1000.0,
                    ancestralPopulationSize=100.0
                )
                """);

        GompertzGrowth_f0 model = assertInstanceOf(GompertzGrowth_f0.class, result);
        assertEquals(0.2, model.f0Input.get().get());
        assertEquals(0.3, model.bInput.get().get());
        assertEquals(1000.0, model.N0Input.get().get());
        assertEquals(100.0, model.NAInput.get().get());
        assertEquals(1, model.indicatorParameterInput.get().get());
    }

    @Test
    public void buildsGompertzT50ModelWithAncestralSize() throws IOException {
        Object result = build("""
                use popfunc.functions.coalescent

                PopulationFunction population = gompertzT50PopulationFunction(
                    halfCapacityAge=5.0,
                    growthRate=0.3,
                    carryingCapacity=1000.0,
                    ancestralPopulationSize=100.0
                )
                """);

        GompertzGrowth_t50 model = assertInstanceOf(GompertzGrowth_t50.class, result);
        assertEquals(5.0, model.t50Input.get().get());
        assertEquals(0.3, model.bInput.get().get());
        assertEquals(1000.0, model.NInfinityInput.get().get());
        assertEquals(100.0, model.NAInput.get().get());
        assertEquals(1, model.indicatorParameterInput.get().get());
    }

    private static Object build(String source) throws IOException {
        List<Stmt> statements = parse(source);
        List<TileLibrary<BEASTState>> libraries =
                TileLibrary.select(BEASTState.class, List.of("popfunc", "beast2"));

        ComponentResolver resolver =
                new ComponentResolver(TileLibrary.loadComponentLibraries(libraries));
        new TypeResolver(resolver).visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> candidates = TileLibrary.combine(libraries);
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(candidates, variableResolver, stochasticityResolver);
        List<Tile<?, BEASTState>> selectedTiles = evaluator.getBestTiling(statements);

        assertEquals(1, selectedTiles.size());
        return selectedTiles
                .getFirst()
                .apply(new BEASTState("popfunc-package-model"), new IdentityHashMap<>());
    }

    private static List<Stmt> parse(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        return new EvaluateLiterals().transform(statements);
    }
}
