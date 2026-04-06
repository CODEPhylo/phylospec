import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BEASTState;
import tiling.EvaluateTiles;
import tiles.TileLibrary;

import java.io.IOException;
import java.util.List;

public class Test2 {
    static void main(String[] args) {
        String source = """
        Real x ~ Normal(mean=0.1, sd=2.2)
        Real z = 100.0
        Real y ~ Normal(mean=x + z, sd=1.0)
        """;

        ComponentResolver componentResolver = loadComponentResolver();

         // run lexer

        List<Token> tokens = new Lexer(source).scanTokens();

        // run parser

        List<Stmt> statements = new Parser(tokens).parse();

        // simplify graph

        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        // run variable resolver

        VariableResolver variableResolver = new VariableResolver(statements);

        // run type resolver

        TypeResolver typeResolver = new TypeResolver(componentResolver);
        typeResolver.visitStatements(statements);

        // perform tiling

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.getTiles(), typeResolver, variableResolver);
        BEASTState result = applyTiles.applyBestTiling(statements);

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
