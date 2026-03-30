import beast.base.core.BEASTObject;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;
import patternmatching.EvaluateTiles;
import patternmatching.Tile;
import tiles.LiteralTile;
import tiles.NormalTile;

import java.io.IOException;
import java.util.List;

public class Test2 {
    static void main(String[] args) {
        String source = "Real a ~ Normal(mean=0.0, sd=4.0)";

        ComponentResolver componentResolver = loadComponentResolver();

         // run lexer

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        // run parser

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        // define tiles

        List<Tile> tiles = List.of(new NormalTile(), new LiteralTile());

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(tiles, typeResolver);
        Object result = applyTiles.visitStatements(statements);

        ((BEASTObject) result).initAndValidate();

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
