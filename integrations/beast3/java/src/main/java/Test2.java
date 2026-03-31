import beast.base.core.BEASTObject;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;
import patternmatching.EvaluateTiles;
import patternmatching.EvaluatedTile;
import patternmatching.Tile;
import tiles.DrawTile;
import tiles.LiteralTile;
import tiles.NormalTile;

import java.io.IOException;
import java.util.List;

public class Test2 {
    static void main(String[] args) {
        String source = """
        Real mean ~ Normal(mean=0.0, sd=1.0)
        // Real data ~ Normal(mean, sd=4.0) // observed as 20.0
        """;

        ComponentResolver componentResolver = loadComponentResolver();

         // run lexer

        List<Token> tokens = new Lexer(source).scanTokens();

        // run parser

        List<Stmt> statements = new Parser(tokens).parse();

        // simplify graph

        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        // define tiles

        List<Tile> tiles = List.of(new NormalTile(), new LiteralTile(), new DrawTile());

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(tiles, typeResolver);
        EvaluatedTile result = applyTiles.visitStatements(statements);

        ((BEASTObject) result.generatedObject()).initAndValidate();

        System.out.println(result);
    }

    private static ComponentResolver loadComponentResolver() {
        List<ComponentLibrary> componentLibraries = null;
        try {
            componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ComponentResolver(componentLibraries);
    }

}
