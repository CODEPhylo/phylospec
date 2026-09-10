package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beast.base.evolution.tree.coalescent.PopulationFunction;
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
import popfunc.beast.evolution.populationmodel.ConstantGrowth;
import popfunc.beast.evolution.populationmodel.ExponentialGrowth;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;

public class StochasticPopulationSelectionEndToEndTest {

    @Test
    public void buildsPackageDefinedGeneratorFromPhyloSpec() throws IOException {
        String source = """
                use popfunc.functions.coalescent

                PopulationFunction first = constantPopulationFunction(
                    populationSize=1000.0
                )
                PopulationFunction second = exponentialPopulationFunction(
                    populationSize=500.0,
                    growthRate=0.25
                )
                PopulationFunction selected = stochasticPopulationSelection(
                    indicator=1,
                    models=[first, second]
                )
                """;

        List<Stmt> statements = parse(source);
        List<TileLibrary<BEASTState>> libraries =
                TileLibrary.select(BEASTState.class, List.of("popfunc", "beast2"));

        ComponentResolver componentResolver =
                new ComponentResolver(TileLibrary.loadComponentLibraries(libraries));
        new TypeResolver(componentResolver).visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> candidates = TileLibrary.combine(libraries);
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(candidates, variableResolver, stochasticityResolver);

        List<Tile<?, BEASTState>> selectedTiles = evaluator.getBestTiling(statements);
        assertEquals(1, selectedTiles.size());

        BEASTState state = new BEASTState("popfunc-selection-e2e");
        Object result = selectedTiles.getFirst().apply(state, new IdentityHashMap<>());

        StochasticVariableSelection selection =
                assertInstanceOf(StochasticVariableSelection.class, result);
        assertEquals(1, selection.indicatorInput.get().get());

        List<PopulationFunction> models = selection.modelsInput.get();
        assertEquals(2, models.size());
        assertInstanceOf(ConstantGrowth.class, models.get(0));
        assertInstanceOf(ExponentialGrowth.class, models.get(1));
    }

    private static List<Stmt> parse(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        return new EvaluateLiterals().transform(statements);
    }
}
