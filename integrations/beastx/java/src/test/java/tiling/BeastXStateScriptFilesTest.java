package tiling;

import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastXCoreTileLibrary;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeastXStateScriptFilesTest {

    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedBeastXState() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/tiling"));
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedBeastXState(psFile));
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

    private DynamicTest assertScriptMatchesExpectedBeastXState(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);
        List<String> expectedStateLines = extractExpectedBlockLines(lines, "EXPECTED BEASTX STATE");
        String source = String.join("\n", lines);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            if (expectedStateLines == null) {
                return;
            }

            List<Token> tokens = new Lexer(source).scanTokens();
            List<Stmt> statements = new Parser(tokens).parse();

            statements = new RemoveGroupings().transform(statements);
            statements = new EvaluateLiterals().transform(statements);

            VariableResolver variableResolver = new VariableResolver(statements);

            StochasticityResolver stochasticityResolver = new StochasticityResolver();
            stochasticityResolver.visitStatements(statements);

            EvaluateTiles<BeastXState> evaluateTiles =
                    new EvaluateTiles<>(
                            new BeastXCoreTileLibrary().getTiles(),
                            variableResolver,
                            stochasticityResolver
                    );

            List<Tile<?, BeastXState>> bestTilings = null;
            try {
                bestTilings = evaluateTiles.getBestTiling(statements);
            } catch (TileApplicationError e) {
                assertExpectedNoState(expectedStateLines, psPath);
                return;
            }

            boolean tilingSucceeded =
                    bestTilings != null && bestTilings.stream().noneMatch(tile -> tile == null);

            if (!tilingSucceeded) {
                assertExpectedNoState(expectedStateLines, psPath);
                return;
            }

            BeastXState beastState =
                    new BeastXState("test");

            PrintStream original =
                    System.out;

            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            try {
                for (Tile<?, BeastXState> tile : bestTilings) {
                    tile.apply(beastState, new IdentityHashMap<>());
                }
            } catch (TileApplicationError e) {
                assertExpectedNoState(expectedStateLines, psPath);
                return;
            } finally {
                System.setOut(original);
            }

            Set<String> actualStateNodeIds =
                    beastState.stateNodes.keySet().stream()
                            .map(Parameter::getId)
                            .collect(Collectors.toSet());

            Set<String> actualCalculationNodeIds =
                    beastState.calculationNodes.keySet().stream()
                            .map(Statistic::getId)
                            .collect(Collectors.toSet());

            Set<String> actualPriorIds =
                    beastState.priorDistributions.values().stream()
                            .map(AbstractDistributionLikelihood::getId)
                            .collect(Collectors.toSet());

            Set<String> actualTreeModelIds =
                    Stream.concat(
                                    beastState.treePriorDistributions.keySet().stream(),
                                    beastState.observedTreeDistributions.keySet().stream()
                            )
                            .map(TreeModel::getId)
                            .collect(Collectors.toSet());

            Set<String> actualTreePriorIds =
                    beastState.treePriorDistributions.values().stream()
                            .map(AbstractModelLikelihood::getId)
                            .collect(Collectors.toSet());

            Set<String> actualCalibrationPriorIds =
                    beastState.calibrationPriorDistributions.stream()
                            .map(AbstractDistributionLikelihood::getId)
                            .collect(Collectors.toSet());

            Set<String> actualLikelihoodIds =
                    Stream.concat(
                                    beastState.likelihoodDistributions.stream(),
                                    beastState.observedTreeDistributions.values().stream()
                            )
                            .map(Likelihood::getId)
                            .collect(Collectors.toSet());

            ExpectedBeastXState expected =
                    parseExpectedState(expectedStateLines);

            assertEquals(
                    expected.stateNodeIds,
                    actualStateNodeIds,
                    "State node ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.calculationNodeIds,
                    actualCalculationNodeIds,
                    "Calculation node ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.priorIds,
                    actualPriorIds,
                    "Prior distribution ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.treeModelIds,
                    actualTreeModelIds,
                    "Tree model ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.treePriorIds,
                    actualTreePriorIds,
                    "Tree prior distribution ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.calibrationPriorIds,
                    actualCalibrationPriorIds,
                    "Calibration prior distribution ID mismatch for: " + psPath
            );

            assertEquals(
                    expected.likelihoodIds,
                    actualLikelihoodIds,
                    "Likelihood distribution ID mismatch for: " + psPath
            );
        });
    }

    private void assertExpectedNoState(
            List<String> expectedStateLines,
            Path psPath
    ) {
        assertEquals(
                1,
                expectedStateLines.size(),
                "Expected exactly one NO_STATE line for failed state construction: " + psPath
        );

        assertEquals(
                "NO_STATE",
                expectedStateLines.get(0).trim(),
                "Expected NO_STATE for failed state construction: " + psPath
        );
    }

    private ExpectedBeastXState parseExpectedState(List<String> expectedStateLines) {
        ExpectedBeastXState expected =
                new ExpectedBeastXState();

        for (String line : expectedStateLines) {
            String trimmed =
                    line.trim();

            if (trimmed.startsWith("SN: ")) {
                parseCommaSeparated(trimmed.substring(4), expected.stateNodeIds);
            } else if (trimmed.startsWith("CN: ")) {
                parseCommaSeparated(trimmed.substring(4), expected.calculationNodeIds);
            } else if (trimmed.startsWith("P: ")) {
                parseCommaSeparated(trimmed.substring(3), expected.priorIds);
            } else if (trimmed.startsWith("TM: ")) {
                parseCommaSeparated(trimmed.substring(4), expected.treeModelIds);
            } else if (trimmed.startsWith("TP: ")) {
                parseCommaSeparated(trimmed.substring(4), expected.treePriorIds);
            } else if (trimmed.startsWith("CP: ")) {
                parseCommaSeparated(trimmed.substring(4), expected.calibrationPriorIds);
            } else if (trimmed.startsWith("L: ")) {
                parseCommaSeparated(trimmed.substring(3), expected.likelihoodIds);
            }
        }

        return expected;
    }

    private void parseCommaSeparated(
            String value,
            Set<String> target
    ) {
        for (String part : value.split(",")) {
            String trimmed =
                    part.trim();

            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private List<String> extractExpectedBlockLines(
            List<String> lines,
            String blockTag
    ) {
        List<String> expected =
                new ArrayList<>();

        int expectStart =
                -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// " + blockTag)) {
                expectStart = i + 1;
                break;
            }
        }

        if (expectStart == -1) {
            return null;
        }

        int expectEnd =
                lines.size();

        for (int i = expectStart; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// " + blockTag)) {
                expectEnd = i;
                break;
            }
        }

        for (int i = expectStart; i < expectEnd; i++) {
            String raw =
                    lines.get(i);

            String trimmed =
                    raw.trim();

            if (!trimmed.startsWith("//")) {
                break;
            }

            int idx =
                    raw.indexOf("//");

            String content =
                    raw.substring(idx + 2);

            if (!content.isEmpty() && content.charAt(0) == ' ') {
                content = content.substring(1);
            }

            expected.add(content);
        }

        return expected;
    }

    private static class ExpectedBeastXState {
        private final Set<String> stateNodeIds = new HashSet<>();
        private final Set<String> calculationNodeIds = new HashSet<>();
        private final Set<String> priorIds = new HashSet<>();
        private final Set<String> treeModelIds = new HashSet<>();
        private final Set<String> treePriorIds = new HashSet<>();
        private final Set<String> calibrationPriorIds = new HashSet<>();
        private final Set<String> likelihoodIds = new HashSet<>();
    }
}
