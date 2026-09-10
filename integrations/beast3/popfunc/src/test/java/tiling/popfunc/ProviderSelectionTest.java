package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.ConstantGrowth;

public class ProviderSelectionTest {

    private static final String SOURCE =
            "PopulationFunction population = constantPopulationFunction(populationSize=500.0)";

    @Test
    public void popFuncCanOverrideEquivalentBaseMapping() throws IOException {
        Object result = build(TileLibrary.loadSelected(
                BEASTState.class, List.of("popfunc", "beast2")));

        assertInstanceOf(ConstantGrowth.class, result);
    }

    @Test
    public void baseCanBePreferredOverPopFuncMapping() throws IOException {
        Object result = build(TileLibrary.loadSelected(
                BEASTState.class, List.of("beast2", "popfunc")));

        assertInstanceOf(ConstantPopulation.class, result);
    }

    @Test
    public void unconfiguredDuplicateProvidersProduceClearError() throws IOException {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> build(TileLibrary.loadAll(BEASTState.class)));

        assertTrue(error.getMessage().contains("Ambiguous engine mapping."));
        assertTrue(error.getMessage().contains("constantPopulationFunction"));
    }

    private static Object build(List<CandidateTile<BEASTState>> candidates) throws IOException {
        List<Stmt> statements = new Parser(new Lexer(SOURCE).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(candidates, variableResolver, stochasticityResolver);
        List<Tile<?, BEASTState>> selectedTiles = evaluator.getBestTiling(statements);

        return selectedTiles
                .getFirst()
                .apply(new BEASTState("provider-selection"), new IdentityHashMap<>());
    }
}
