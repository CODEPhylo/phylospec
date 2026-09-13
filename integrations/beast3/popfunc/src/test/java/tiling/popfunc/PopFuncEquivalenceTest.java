package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import beast.base.spec.domain.NonNegativeInt;
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

    private static void assertNumericallyEquivalent(
            GompertzGrowth_f0 reference, GompertzGrowth_f0 generated) {
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
