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
        Alignment data = fromNexus("/Users/ochsneto/Documents/PhyloSpec/beast3/beast-base/src/test/resources/beast.base/examples/nexus/primate-mtDNA.nex")
        
        Tree tree ~ Yule(
            birthRate=1.0, taxa=taxa(data)
        )
        
        Alignment alignment ~ PhyloCTMC(
          tree,
          branchRates~StrictClock(rate=1.0, tree=tree),
          qMatrix=jc69(),
          siteRates~DiscreteGammaInv(
            shape=1.0, numCategories=4, numSites=100
          )
        ) observed as data
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

        EvaluateTiles applyTiles = new EvaluateTiles(TileLibrary.getTiles(), variableResolver);
        BEASTState result = applyTiles.applyBestTiling(statements);
        result.initializeBEASTObjects();

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
