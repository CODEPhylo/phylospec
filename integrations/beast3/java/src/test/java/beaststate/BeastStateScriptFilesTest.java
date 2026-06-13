package beaststate;

import beastconfig.BEASTState;
import beast.base.inference.CalculationNode;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.*;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.OperatorTileLibrary;
import tiles.TileLibrary;
import tiling.EvaluateTiles;
import tiling.Tile;
import tiling.TileApplicationError;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class BeastStateScriptFilesTest {

    @TestFactory
    public Iterable<DynamicTest> testAllPhylospecFiles() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/tiling"));
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedState(psFile));
        }

        return tests;
    }

    private List<Path> findPsFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".phylospec"))
                    .collect(Collectors.toList());
        }
    }

    private DynamicTest assertScriptMatchesExpectedState(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);
        List<String> expectedStateLines = extractExpectedBlockLines(lines, "EXPECTED BEAST STATE");
        String source = String.join("\n", lines);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            // lex and parse

            List<Token> tokens = new Lexer(source).scanTokens();
            List<Stmt> statements = new Parser(tokens).parse();

            // simplify

            statements = new RemoveGroupings().transform(statements);
            statements = new EvaluateLiterals().transform(statements);

            // resolve variables and stochasticity

            VariableResolver variableResolver = new VariableResolver(statements);

            StochasticityResolver stochasticityResolver = new StochasticityResolver();
            stochasticityResolver.visitStatements(statements);

            // tile each statement

            EvaluateTiles evaluateTiles = new EvaluateTiles(TileLibrary.loadAll(), OperatorTileLibrary.getTiles(), variableResolver, stochasticityResolver);
            List<Tile<?>> bestTilings = null;
            try {
                bestTilings = evaluateTiles.getBestTiling(statements);
            } catch (TileApplicationError e) {
                // tiling failed
            }

            boolean tilingSucceeded = bestTilings != null && bestTilings.stream().noneMatch(t -> t == null);
            if (!tilingSucceeded) {
                assertNotNull(expectedStateLines, "Tiling failed but no EXPECTED BEAST STATE block found in: " + psPath);
                assertTrue(expectedStateLines.size() == 1 && expectedStateLines.get(0).trim().equals("NO_STATE"),
                        "Tiling failed but EXPECTED BEAST STATE does not contain NO_STATE in: " + psPath);
                return;
            }

            assertNotNull(expectedStateLines, "Tiling succeeded but no EXPECTED BEAST STATE block found in: " + psPath);

            // apply tiles and build BEAST state

            BEASTState beastState = new BEASTState("test");
            PrintStream original = System.out;
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            try {
                for (Tile<?> tile : bestTilings) {
                    tile.apply(beastState, new IdentityHashMap<>());
                }
            } catch (TileApplicationError e) {
                return;
            } finally {
                System.setOut(original);
            }

            // collect actual state IDs

            Set<String> actualStateNodeIds = beastState.stateNodes.keySet().stream()
                    .map(StateNode::getID)
                    .collect(Collectors.toSet());

            Set<String> actualCalculationNodeIds = beastState.calculationNodes.keySet().stream()
                    .map(CalculationNode::getID)
                    .collect(Collectors.toSet());

            Set<String> actualPriorIds = beastState.priorDistributions.values().stream()
                    .map(Distribution::getID)
                    .collect(Collectors.toSet());

            Set<String> actualLikelihoodIds = beastState.likelihoodDistributions.stream()
                    .map(Distribution::getID)
                    .collect(Collectors.toSet());

            // parse expected IDs from comments

            Set<String> expectedStateNodeIds = new HashSet<>();
            Set<String> expectedCalculationNodeIds = new HashSet<>();
            Set<String> expectedPriorIds = new HashSet<>();
            Set<String> expectedLikelihoodIds = new HashSet<>();

            for (String line : expectedStateLines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("SN: ")) {
                    parseCommaSeparated(trimmed.substring(4), expectedStateNodeIds);
                } else if (trimmed.startsWith("CN: ")) {
                    parseCommaSeparated(trimmed.substring(4), expectedCalculationNodeIds);
                } else if (trimmed.startsWith("P: ")) {
                    parseCommaSeparated(trimmed.substring(3), expectedPriorIds);
                } else if (trimmed.startsWith("L: ")) {
                    parseCommaSeparated(trimmed.substring(3), expectedLikelihoodIds);
                }
            }

            // compare

            assertEquals(expectedStateNodeIds, actualStateNodeIds, "State node ID mismatch for: " + psPath);
            assertEquals(expectedCalculationNodeIds, actualCalculationNodeIds, "Calculation node ID mismatch for: " + psPath);
            assertEquals(expectedPriorIds, actualPriorIds, "Prior distribution ID mismatch for: " + psPath);
            assertEquals(expectedLikelihoodIds, actualLikelihoodIds, "Likelihood distribution ID mismatch for: " + psPath);
        });
    }

    private void parseCommaSeparated(String value, Set<String> target) {
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) target.add(trimmed);
        }
    }

    private List<String> extractExpectedBlockLines(List<String> lines, String blockTag) {
        List<String> expected = new ArrayList<>();

        int expectStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// " + blockTag)) {
                expectStart = i + 1;
                break;
            }
        }

        if (expectStart == -1) return null;

        int expectEnd = lines.size();
        for (int i = expectStart; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// " + blockTag)) {
                expectEnd = i;
                break;
            }
        }

        for (int i = expectStart; i < expectEnd; i++) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (!trimmed.startsWith("//")) break;
            int idx = raw.indexOf("//");
            String content = raw.substring(idx + 2);
            if (!content.isEmpty() && content.charAt(0) == ' ') {
                content = content.substring(1);
            }
            expected.add(content);
        }

        return expected;
    }

}
