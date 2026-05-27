import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.types.IntScalar;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BeastXMatrixDimensionTileTest {

    @Test
    public void computesMatrixRows() {
        Object rows =
                applySingleStatement(
                        "Integer rows = numRows(matrix=[[1.0, 2.0], [3.0, 4.0], [5.0, 6.0]])"
                );

        IntScalar<?> rowCount =
                assertInstanceOf(IntScalar.class, rows);

        assertEquals(3, rowCount.get());
    }

    @Test
    public void computesMatrixColumns() {
        Object cols =
                applySingleStatement(
                        "Integer cols = numCols(matrix=[[1.0, 2.0], [3.0, 4.0], [5.0, 6.0]])"
                );

        IntScalar<?> colCount =
                assertInstanceOf(IntScalar.class, cols);

        assertEquals(2, colCount.get());
    }

    private Object applySingleStatement(String source) {
        List<Tile<?, BeastXState>> bestTilings =
                getBestTilings(source);

        assertEquals(1, bestTilings.size());

        BeastXState state =
                new BeastXState("test");

        return bestTilings.getFirst().apply(state, new IdentityHashMap<>());
    }

    private List<Tile<?, BeastXState>> getBestTilings(String source) {
        List<Token> tokens =
                new Lexer(source).scanTokens();

        List<Stmt> statements =
                new Parser(tokens).parse();

        statements =
                new RemoveGroupings().transform(statements);

        statements =
                new EvaluateLiterals().transform(statements);

        statements =
                new EvaluateScalarFunctions().transform(statements);

        VariableResolver variableResolver =
                new VariableResolver(statements);

        StochasticityResolver stochasticityResolver =
                new StochasticityResolver();

        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BeastXState> evaluateTiles =
                new EvaluateTiles<>(
                        new BeastXCoreTileLibrary().getTiles(),
                        new ArrayList<>(),
                        variableResolver,
                        stochasticityResolver
                );

        return evaluateTiles.getBestTiling(statements);
    }
}