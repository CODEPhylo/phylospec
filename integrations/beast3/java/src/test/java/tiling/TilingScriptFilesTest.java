package tiling;

import beastconfig.BEASTState;
import tiles.OperatorTileLibrary;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastCoreTileLibrary;
import tiles.TileLibrary;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TilingScriptFilesTest {

    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedTiles() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/tiling"));
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedTiles(psFile));
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

    private DynamicTest assertScriptMatchesExpectedTiles(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);
        List<String> expectedTileLines = extractExpectedBlockLines(lines, "EXPECTED_TILES");
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

            // tile each statement, skipping imports (which have no tile by design)

            List<String> actualTileLines = new ArrayList<>();

            EvaluateTiles evaluateTiles = new EvaluateTiles(TileLibrary.loadAll(), OperatorTileLibrary.getTiles(), variableResolver, stochasticityResolver);
            List<Tile<?>> bestTilings = null;
            try {
                bestTilings = evaluateTiles.getBestTiling(statements);
                for (Tile<?> bestTiling : bestTilings) {
                    actualTileLines.add(bestTiling != null ? "TILING_SUCCESS" : "NO_VALID_TILING");
                }
            } catch (TileApplicationError e) {
                actualTileLines.add(e.getMessage());
            }

            assertEquals(expectedTileLines.size(), actualTileLines.size(), "Wrong number of tile lines for: " + psPath);
            for (int i = 0; i < expectedTileLines.size(); i++) {
                String expected = expectedTileLines.get(i).trim();
                String actual = actualTileLines.get(i).trim();
                if (!actual.equals("TILING_SUCCESS")) {
                    assertEquals(expected, actual, "Tile mismatch at index " + i + " for: " + psPath);
                }
            }

            // apply tiles if tiling succeeded and compare application errors

            List<String> expectedApplicationErrorLines = extractExpectedBlockLines(lines, "EXPECTED_APPLICATION_ERRORS");
            List<String> actualApplicationErrorLines = new ArrayList<>();

            boolean tilingSucceeded = bestTilings != null && bestTilings.stream().noneMatch(t -> t == null);
            if (tilingSucceeded) {
                PrintStream original = System.out;
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
                try {
                    BEASTState beastState = new BEASTState("test");
                    for (Tile<?> tile : bestTilings) {
                        tile.apply(beastState);
                    }
                } catch (TileApplicationError e) {
                    actualApplicationErrorLines.add(e.getMessage());
                } finally {
                    System.setOut(original);
                }
            }

            assertEquals(expectedApplicationErrorLines.size(), actualApplicationErrorLines.size(), "Wrong number of application error lines for: " + psPath);
            for (int i = 0; i < expectedApplicationErrorLines.size(); i++) {
                assertEquals(expectedApplicationErrorLines.get(i).trim(), actualApplicationErrorLines.get(i).trim().lines().findFirst().orElseThrow(), "Application error mismatch at index " + i + " for: " + psPath);
            }
        });
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

        if (expectStart == -1) return expected;

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

    private ComponentResolver loadComponentResolver() {
        try {
            List<ComponentLibrary> libraries = ComponentResolver.loadCoreComponentLibraries();
            return new ComponentResolver(libraries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
