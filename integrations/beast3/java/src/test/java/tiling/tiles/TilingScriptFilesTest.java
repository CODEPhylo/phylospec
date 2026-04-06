package tiling.tiles;

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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.EvaluateTiles;
import tiling.Tile;
import tiles.TileLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TilingScriptFilesTest {

    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedTiles() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/tiling/tiles"));
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
        List<String> expectedTileLines = extractExpectedTileLines(lines);
        String source = String.join("\n", lines);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            // lex and parse

            List<Token> tokens = new Lexer(source).scanTokens();
            List<Stmt> statements = new Parser(tokens).parse();

            // simplify

            statements = new RemoveGroupings().transform(statements);
            statements = new EvaluateLiterals().transform(statements);

            // resolve variables

            VariableResolver variableResolver = new VariableResolver(statements);

            // tile each statement, skipping imports (which have no tile by design)

            EvaluateTiles evaluateTiles = new EvaluateTiles(TileLibrary.getTiles(), variableResolver);
            List<Tile<?>> bestTilings = evaluateTiles.getBestTiling(statements);

            List<String> actualTileLines = new ArrayList<>();
            for (Tile<?> bestTiling : bestTilings) {
                actualTileLines.add(bestTiling != null ? bestTiling.toString() : "NO_VALID_TILING");
            }

            assertEquals(expectedTileLines.size(), actualTileLines.size(), "Wrong number of tile lines for: " + psPath);
            for (int i = 0; i < expectedTileLines.size(); i++) {
                assertEquals(expectedTileLines.get(i).trim(), actualTileLines.get(i).trim(), "Tile mismatch at index " + i + " for: " + psPath);
            }
        });
    }

    private List<String> extractExpectedTileLines(List<String> lines) {
        List<String> expected = new ArrayList<>();

        int expectStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// EXPECTED_TILES")) {
                expectStart = i + 1;
                break;
            }
        }

        if (expectStart == -1) return expected;

        int expectEnd = lines.size();
        for (int i = expectStart; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("// EXPECTED_TILES")) {
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
