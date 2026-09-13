package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;
import popfunc.beast.evolution.populationmodel.LogisticGrowth;

public class PopFuncEquivalenceTest {

    private static final double[] TIMES = {0.0, 0.5, 2.0, 5.0};

    @Test
    public void matchesDirectGompertzF0WithoutAncestralPopulation() throws IOException {
        GompertzGrowth_f0 generated = buildGompertzF0(null);
        GompertzGrowth_f0 reference = directGompertzF0(null);

        assertNull(generated.NAInput.get());
        assertEquals(0, generated.indicatorParameterInput.get().get());
        assertNumericallyEquivalent(reference, generated);
    }

    @Test
    public void matchesDirectGompertzF0WithAncestralPopulation() throws IOException {
        GompertzGrowth_f0 generated = buildGompertzF0(100.0);
        GompertzGrowth_f0 reference = directGompertzF0(100.0);

        assertEquals(100.0, generated.NAInput.get().get());
        assertEquals(1, generated.indicatorParameterInput.get().get());
        assertNumericallyEquivalent(reference, generated);
    }

    @Test
    public void matchesDirectGompertzT50WithoutAncestralPopulation() throws IOException {
        GompertzGrowth_t50 generated = buildGompertzT50(null);
        GompertzGrowth_t50 reference = directGompertzT50(null);

        assertNull(generated.NAInput.get());
        assertEquals(0, generated.indicatorParameterInput.get().get());
        assertNumericallyEquivalent(reference, generated);
    }

    @Test
    public void matchesDirectGompertzT50WithAncestralPopulation() throws IOException {
        GompertzGrowth_t50 generated = buildGompertzT50(100.0);
        GompertzGrowth_t50 reference = directGompertzT50(100.0);

        assertEquals(100.0, generated.NAInput.get().get());
        assertEquals(1, generated.indicatorParameterInput.get().get());
        assertNumericallyEquivalent(reference, generated);
    }

    @Test
    public void preferredPopFuncLogisticMatchesDirectModelWithoutAncestralPopulation()
            throws IOException {
        LogisticGrowth generated = buildLogistic(null);
        LogisticGrowth reference = directLogistic(null);

        assertEquals(0.0, generated.getRawNA());
        assertNumericallyEquivalent(reference, generated);
    }

    @Test
    public void preferredPopFuncLogisticMatchesDirectModelWithAncestralPopulation()
            throws IOException {
        LogisticGrowth generated = buildLogistic(100.0);
        LogisticGrowth reference = directLogistic(100.0);

        assertEquals(100.0, generated.getRawNA());
        assertEquals(100.0, generated.getEffectiveNA());
        assertNumericallyEquivalent(reference, generated);
    }

    private static GompertzGrowth_f0 buildGompertzF0(Double ancestralSize)
            throws IOException {
        String optionalArgument = ancestralSize == null
                ? ""
                : ", ancestralPopulationSize=" + ancestralSize;
        String source = """
                use popfunc.functions.coalescent

                PopulationFunction population = gompertzF0PopulationFunction(
                    initialProportion=0.2,
                    growthRate=0.3,
                    initialPopulationSize=1000.0%s
                )
                """.formatted(optionalArgument);

        return assertInstanceOf(GompertzGrowth_f0.class, build(source));
    }

    private static GompertzGrowth_f0 directGompertzF0(Double ancestralSize) {
        GompertzGrowth_f0 model = new GompertzGrowth_f0();
        model.f0Input.setValue(new RealScalarParam<>(0.2, PositiveReal.INSTANCE), model);
        model.bInput.setValue(new RealScalarParam<>(0.3, PositiveReal.INSTANCE), model);
        model.N0Input.setValue(new RealScalarParam<>(1000.0, PositiveReal.INSTANCE), model);

        int indicator = 0;
        if (ancestralSize != null) {
            model.NAInput.setValue(
                    new RealScalarParam<>(ancestralSize, PositiveReal.INSTANCE), model);
            indicator = 1;
        }
        model.indicatorParameterInput.setValue(
                new IntScalarParam<>(indicator, NonNegativeInt.INSTANCE), model);
        model.initAndValidate();
        return model;
    }

    private static GompertzGrowth_t50 buildGompertzT50(Double ancestralSize)
            throws IOException {
        String optionalArgument = ancestralSize == null
                ? ""
                : ", ancestralPopulationSize=" + ancestralSize;
        String source = """
                use popfunc.functions.coalescent

                PopulationFunction population = gompertzT50PopulationFunction(
                    halfCapacityAge=5.0,
                    growthRate=0.3,
                    carryingCapacity=1000.0%s
                )
                """.formatted(optionalArgument);

        return assertInstanceOf(GompertzGrowth_t50.class, build(source));
    }

    private static GompertzGrowth_t50 directGompertzT50(Double ancestralSize) {
        GompertzGrowth_t50 model = new GompertzGrowth_t50();
        model.t50Input.setValue(
                new RealScalarParam<>(5.0, NonNegativeReal.INSTANCE), model);
        model.bInput.setValue(new RealScalarParam<>(0.3, PositiveReal.INSTANCE), model);
        model.NInfinityInput.setValue(
                new RealScalarParam<>(1000.0, PositiveReal.INSTANCE), model);

        int indicator = 0;
        if (ancestralSize != null) {
            model.NAInput.setValue(
                    new RealScalarParam<>(ancestralSize, PositiveReal.INSTANCE), model);
            indicator = 1;
        }
        model.indicatorParameterInput.setValue(
                new IntScalarParam<>(indicator, NonNegativeInt.INSTANCE), model);
        model.initAndValidate();
        return model;
    }

    private static LogisticGrowth buildLogistic(Double ancestralSize) throws IOException {
        String optionalArgument = ancestralSize == null
                ? ""
                : ", ancestralPopulationSize=" + ancestralSize;
        String source = """
                PopulationFunction population = logisticPopulationFunction(
                    inflectionAge=5.0,
                    carryingCapacity=1000.0,
                    growthRate=0.25%s
                )
                """.formatted(optionalArgument);

        return assertInstanceOf(LogisticGrowth.class, build(source));
    }

    private static LogisticGrowth directLogistic(Double ancestralSize) {
        LogisticGrowth model = new LogisticGrowth();
        model.t50Input.setValue(
                new RealScalarParam<>(5.0, NonNegativeReal.INSTANCE), model);
        model.nCarryingCapacityInput.setValue(
                new RealScalarParam<>(1000.0, PositiveReal.INSTANCE), model);
        model.bInput.setValue(
                new RealScalarParam<>(0.25, NonNegativeReal.INSTANCE), model);
        if (ancestralSize != null) {
            model.NAInput.setValue(
                    new RealScalarParam<>(ancestralSize, NonNegativeReal.INSTANCE), model);
        }
        model.initAndValidate();
        return model;
    }

    private static void assertNumericallyEquivalent(
            PopulationFunction reference, PopulationFunction generated) {
        for (double time : TIMES) {
            assertClose(reference.getPopSize(time), generated.getPopSize(time));
            assertClose(reference.getIntensity(time), generated.getIntensity(time));
        }
    }

    private static void assertClose(double expected, double actual) {
        double tolerance = Math.max(1.0e-12, Math.abs(expected) * 1.0e-10);
        assertEquals(expected, actual, tolerance);
    }

    private static Object build(String source) throws IOException {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

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
                .apply(new BEASTState("popfunc-equivalence"), new IdentityHashMap<>());
    }
}
