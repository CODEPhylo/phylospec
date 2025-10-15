package org.phylospec.parser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.AstResolver;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScriptFilesResolverTest {

    /**
     * Goes through every .phylospec file in the test folder, parses it, and tests if
     * the variables and types can be resolved.
     */
    @TestFactory
    public Iterable<DynamicTest> testResolveAllPsScripts() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java"));

        // Ensure deterministic order for stable test output
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptResolutionMatchesExpectation(psFile));
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

    private DynamicTest assertScriptResolutionMatchesExpectation(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);

        List<String> expectedResolutionErrors = extractExpectedResolutionErrors(lines);

        String source = String.join("\n", lines);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens);
            List<Stmt> statements = parser.parse();

            ComponentResolver componentResolver = new ComponentResolver();
            componentResolver.registerLibraryFromFile("../../schema/phylospec-core-component-library.json");
            componentResolver.importEntireNamespace(List.of("phylospec"));
            AstResolver resolver = new AstResolver(componentResolver);

            List<String> actualResolutionErrors = new ArrayList<>();
            for (Stmt statement : statements) {
                try {
                    statement.accept(resolver);
                }  catch (AstResolver.ResolutionError e) {
                    actualResolutionErrors.add(e.getMessage());
                }
            }

            for (int i = 0; i < Math.max(expectedResolutionErrors.size(), actualResolutionErrors.size()); i++) {
                if (expectedResolutionErrors.size() <= i) {
                    assertEquals("<no error>", actualResolutionErrors.get(i).trim(), "Resolution error mismatch " + i + " for: " + psPath);
                } else if (actualResolutionErrors.size() <= i) {
                    assertEquals(expectedResolutionErrors.get(i).trim(), "<no error>", "Resolution error mismatch " + i + " for: " + psPath);
                } else {
                    String expected = expectedResolutionErrors.get(i).trim();
                    String actual = actualResolutionErrors.get(i).trim();
                    assertEquals(expected, actual, "Resolution error mismatch " + i + " for: " + psPath);
                }
            }
        });
    }

    private List<String> extractExpectedResolutionErrors(List<String> lines) {
        List<String> expected = new ArrayList<>();

        int expectStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("// EXPECT RESOLUTION")) {
                expectStart = i + 1;
                break;
            }
        }

        int expectEnd = -1;
        for (int i = expectStart + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("// EXPECT RESOLUTION")) {
                expectEnd = i;
                break;
            }
        }

        if (expectStart == -1) {
            return expected;
        }

        for (int i = expectStart; i < expectEnd; i++) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (!trimmed.startsWith("//")) {
                break;
            }

            // Remove leading // and any following single space if present
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


