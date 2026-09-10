package tiling;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.misc.AssignmentTile;

public class AmbiguousGeneratorMappingTest {

    @Test
    public void rejectsEquivalentBestMappingsFromDifferentTileClasses() {
        List<Stmt> statements = new Parser(new Lexer("QMatrix q = jc69()").scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> candidates =
                List.of(new AssignmentTile(), new FirstJc69Tile(), new SecondJc69Tile());
        EvaluateTiles<BEASTState> evaluator = new EvaluateTiles<>(candidates, variableResolver, stochasticityResolver);
        List<Stmt> preparedStatements = statements;

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> evaluator.getBestTiling(preparedStatements));

        assertTrue(error.getMessage().contains("Ambiguous engine mapping."));
        assertTrue(error.getMessage().contains("phylospec.functions.substitution.jc69"));
        assertTrue(error.getMessage().contains(FirstJc69Tile.class.getName()));
        assertTrue(error.getMessage().contains(SecondJc69Tile.class.getName()));
    }

    public abstract static class TestJc69Tile extends GeneratorTile<JukesCantor, BEASTState> {
        @Override
        public TypeToken<?> getTypeToken() {
            return TypeToken.of(JukesCantor.class);
        }

        @Override
        public String getPhyloSpecGeneratorName() {
            return "jc69";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.functions.substitution");
        }

        @Override
        protected JukesCantor applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return new JukesCantor();
        }
    }

    public static final class FirstJc69Tile extends TestJc69Tile {}

    public static final class SecondJc69Tile extends TestJc69Tile {}
}
