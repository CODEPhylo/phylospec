import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beastconfig.OperatorSelector;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.OperatorTileLibrary;
import tiles.TileLibrary;
import beastconfig.BEASTState;
import tiling.EvaluateTiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Beast3OperatorSummaryTest implements ErrorEventListener {

    private static final Path H1N1_SOURCE = Path.of(
            "src/test/java/resources/comparison/legacy/tutorialH1N1DatedExponentialCoalescentHKYGamma.phylospec"
    );

    @Test
    void printsBeast3OperatorsForH1N1CoalescentHKYGammaModel() throws Exception {
        String source = Files.readString(H1N1_SOURCE);

        BEASTState beastState =
                buildBeast3State(source, "h1n1-beast3-operator-summary");

        State state =
                new State();

        beastState.setInput(
                state,
                state.stateNodeInput,
                new ArrayList<>(beastState.stateNodes.keySet())
        );

        for (StateNode stateNode : beastState.stateNodes.keySet()) {
            OperatorSelector.addDefaultOperators(stateNode, beastState);
        }

        List<String> operatorSummary =
                summarizeBeast3Operators(beastState);

        System.out.println();
        System.out.println("==== BEAST 3 operator summary ====");
        operatorSummary.forEach(System.out::println);
        System.out.println("==== end BEAST 3 operator summary ====");
        System.out.println();

        /*
         * These assertions are intentionally looser than the BEAST X assertions,
         * because BEAST 3 operator names may differ from BEAST X operator names.
         * The goal is to check whether each sampled object has a reasonable
         * proposal mechanism.
         */

        assertSummaryMentions(operatorSummary, "clockRate");
        assertSummaryMentions(operatorSummary, "populationSize");
        assertSummaryMentions(operatorSummary, "growthRate");
        assertSummaryMentions(operatorSummary, "kappa");
        assertSummaryMentions(operatorSummary, "gammaShape");
        assertSummaryMentions(operatorSummary, "baseFrequencies");

        assertTrue(
                containsAny(operatorSummary, "tree", "Tree"),
                "Expected BEAST 3 operator summary to contain tree-related operators.\nActual summary:\n"
                        + String.join("\n", operatorSummary)
        );
    }

    private BEASTState buildBeast3State(
            String source,
            String runName
    ) throws Exception {
        ComponentResolver componentResolver =
                loadComponentResolver();

        Lexer lexer =
                new Lexer(source);

        lexer.registerEventListener(this);

        List<Token> tokens =
                lexer.scanTokens();

        Parser parser =
                new Parser(tokens);

        parser.registerEventListener(this);

        List<Stmt> statements =
                parser.parse();

        statements =
                new RemoveGroupings().transform(statements);

        statements =
                new EvaluateLiterals().transform(statements);

        statements =
                new EvaluateScalarFunctions().transform(statements);

        VariableResolver variableResolver =
                new VariableResolver(statements);

        TypeResolver typeResolver =
                new TypeResolver(componentResolver);

        typeResolver.visitStatements(statements);

        StochasticityResolver stochasticityResolver =
                new StochasticityResolver();

        stochasticityResolver.visitStatements(statements);

        EvaluateTiles applyTiles =
                new EvaluateTiles(
                        TileLibrary.loadAll(),
                        OperatorTileLibrary.getTiles(),
                        variableResolver,
                        stochasticityResolver
                );

        BEASTState beastState =
                new BEASTState(runName);

        applyTiles.getBestTiling(statements);

        return applyTiles.applyBestTiling(beastState);
    }

    private static List<String> summarizeBeast3Operators(BEASTState beastState) {
        List<String> summaries =
                new ArrayList<>();

        for (Operator operator : beastState.operators.keySet()) {
            summaries.add(summarizeOperator(operator));
        }

        return summaries;
    }

    private static String summarizeOperator(Operator operator) {
        String id =
                operator.getID();

        String className =
                operator.getClass().getSimpleName();

        String fullClassName =
                operator.getClass().getName();

        return "Operator(class=%s, fullClass=%s, id=%s, raw=%s)".formatted(
                className,
                fullClassName,
                id,
                operator
        );
    }

    private static void assertSummaryMentions(
            List<String> summaries,
            String expectedFragment
    ) {
        assertTrue(
                containsAny(summaries, expectedFragment),
                "Expected BEAST 3 operator summary to mention: " + expectedFragment
                        + "\nActual summary:\n" + String.join("\n", summaries)
        );
    }

    private static boolean containsAny(
            List<String> summaries,
            String... fragments
    ) {
        for (String summary : summaries) {
            for (String fragment : fragments) {
                if (summary.contains(fragment)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ComponentResolver loadComponentResolver() {
        try {
            List<ComponentLibrary> componentLibraries =
                    ComponentResolver.loadCoreComponentLibraries();

            return new ComponentResolver(componentLibraries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void errorDetected(Error error) {
        throw new AssertionError(error.toString());
    }
}
