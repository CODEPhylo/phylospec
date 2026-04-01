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
import tiles.TileLibrary;

import java.io.IOException;
import java.util.List;

public class Test2 {
    static void main(String[] args) {
        String source = """
        Integer seed = 100
        Alignment alignment = fromNexus("test.nex")
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

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.getTiles(), typeResolver);
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
