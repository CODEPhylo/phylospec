package tiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;
import tiles.substitutionmodels.GTRGeneratedTile;
import tiles.substitutionmodels.GTRRelativeRatesTile;

class GTRSelectionTest {

    @Test
    void selectsGeneratedTileForIndividualRates() throws InconsistentTilingException {
        Tile<?, BEASTState> tile = selectModelTile("""
                QMatrix q = gtr(
                    rateAC=2.0,
                    rateAG=1.0,
                    rateAT=2.0,
                    rateCG=1.0,
                    rateCT=2.0,
                    rateGT=5.0,
                    baseFrequencies=[0.25, 0.25, 0.25, 0.25]
                )
                """);

        assertInstanceOf(GTRGeneratedTile.class, tile);
    }

    @Test
    void selectsHandwrittenTileForRelativeRates() throws InconsistentTilingException {
        Tile<?, BEASTState> tile = selectModelTile("""
                QMatrix q = gtr(
                    relativeRates=[0.1, 0.2, 0.1, 0.2, 0.1, 0.3],
                    baseFrequencies=[0.25, 0.25, 0.25, 0.25]
                )
                """);

        assertInstanceOf(GTRRelativeRatesTile.class, tile);
    }

    @SuppressWarnings("unchecked")
    private static Tile<?, BEASTState> selectModelTile(String source) throws InconsistentTilingException {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(new BeastCoreTileLibrary().getTiles(), variableResolver, stochasticityResolver);

        List<Tile<?, BEASTState>> tilings = evaluator.getBestTiling(statements);
        assertEquals(1, tilings.size());

        List<Tile<?, ?>> assignmentInputs =
                List.copyOf(tilings.get(0).getWiredUpInputs().values());
        assertEquals(1, assignmentInputs.size());
        return (Tile<?, BEASTState>) assignmentInputs.get(0);
    }
}
