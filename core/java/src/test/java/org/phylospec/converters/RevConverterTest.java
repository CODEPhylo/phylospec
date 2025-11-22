package org.phylospec.converters;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

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

public class RevConverterTest {

    /**
     * Goes through every .phylospec file in the converters test folder, parses it,
     * converts it to Rev using RevConverter, and compares the result with the
     * corresponding .rev file.
     */
    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedRev() throws IOException {
        Path convertersTestDir = Paths.get("src/test/java/org/phylospec/converters");
        List<Path> psFiles = findPsFiles(convertersTestDir);

        // Ensure deterministic order for stable test output
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedRev(psFile));
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

    private DynamicTest assertScriptMatchesExpectedRev(Path psPath) throws IOException {
        String source = Files.readString(psPath, StandardCharsets.UTF_8);

        // Find corresponding .rev file
        String psFileName = psPath.getFileName().toString();
        String revFileName = psFileName.replace(".phylospec", ".rev");
        Path revPath = psPath.getParent().resolve(revFileName);

        if (!Files.exists(revPath)) {
            return DynamicTest.dynamicTest(
                    psPath.getFileName().toString() + " (missing .rev file)",
                    () -> {
                        System.err.println("Expected Rev file not found: " + revPath);
                    }
            );
        }

        String expectedRev = Files.readString(revPath, StandardCharsets.UTF_8);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            // Lex and parse the PhyloSpec file
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens);
            List<Stmt> statements = parser.parse();

            List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();

            // Convert AST to Rev using RevConverter
            String actualRevString = RevConverter.convertToRev(psPath.getFileName().toString(), statements, componentLibraries).replace("\t", "    ");
            String expectedRevString = expectedRev.trim();

            assertEquals(
                    expectedRevString,
                    actualRevString,
                    "Rev conversion mismatch for: " + psPath
            );
        });
    }
}

