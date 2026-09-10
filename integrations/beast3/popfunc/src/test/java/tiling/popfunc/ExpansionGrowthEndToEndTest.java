package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beastconfig.BEASTState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.ExpansionGrowth;

public class ExpansionGrowthEndToEndTest {

    @Test
    public void buildsExpansionModelFromPhyloSpec() throws IOException {
        String source = """
                PopulationFunction population = expansionPopulationFunction(
                    populationSize=1000.0,
                    growthRate=0.25,
                    transitionAge=5.0,
                    ancestralPopulationSize=100.0
                )
                """;

        ExpansionGrowth expansion = build(source, "popfunc-expansion-e2e");

        assertEquals(1_000.0, expansion.NCInput.get().get());
        assertEquals(0.25, expansion.rInput.get().get());
        assertEquals(5.0, expansion.xInput.get().get());
        assertEquals(100.0, expansion.NAInput.get().get());
        assertEquals(1, expansion.I_naInput.get().get());
    }

    @Test
    public void buildsExpansionModelWithoutOptionalAncestralSize() throws IOException {
        String source = """
                PopulationFunction population = expansionPopulationFunction(
                    populationSize=1000.0,
                    growthRate=0.25,
                    transitionAge=5.0
                )
                """;

        ExpansionGrowth expansion = build(source, "popfunc-expansion-default-e2e");

        assertEquals(1.0, expansion.NAInput.get().get());
        assertEquals(0, expansion.I_naInput.get().get());
    }

    @Test
    public void warnsWhenAncestralSizeIsNotSmallerThanPresentSize() throws IOException {
        String source = """
                PopulationFunction population = expansionPopulationFunction(
                    populationSize=1000.0,
                    growthRate=0.25,
                    transitionAge=5.0,
                    ancestralPopulationSize=1200.0
                )
                """;

        List<Stmt> statements = parse(source);
        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        List<Error> warnings = new ArrayList<>();
        typeResolver.registerEventListener(new ErrorEventListener() {
            @Override
            public void errorDetected(Error error) {}

            @Override
            public void warningDetected(Error warning) {
                warnings.add(warning);
            }
        });

        typeResolver.visitStatements(statements);

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().hint().contains(
                "The value of 'ancestralPopulationSize' must be less than "
                        + "the value of 'populationSize'"));
    }

    private static ExpansionGrowth build(String source, String runName) throws IOException {
        List<Stmt> statements = parse(source);

        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        List<CandidateTile<BEASTState>> candidates =
                TileLibrary.loadSelected(BEASTState.class, List.of("popfunc", "beast2"));
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(candidates, variableResolver, stochasticityResolver);

        BEASTState state = new BEASTState(runName);
        Object result = evaluator.getBestTiling(statements)
                .getFirst()
                .apply(state, new IdentityHashMap<>());
        ExpansionGrowth expansion = assertInstanceOf(ExpansionGrowth.class, result);
        state.initBEASTObject(expansion);
        return expansion;
    }

    private static List<Stmt> parse(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        return new EvaluateLiterals().transform(statements);
    }
}
