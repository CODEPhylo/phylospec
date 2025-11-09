package org.phylospec.converters;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;

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

public class LPhyConverterTest {

    /**
     * Goes through every .phylospec file in the converters test folder, parses it,
     * converts it to LPhy using LPhyConverter, and compares the result with the
     * corresponding .lphy file.
     */
    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedLPhy() throws IOException {
        Path convertersTestDir = Paths.get("src/test/java/org/phylospec/converters");
        List<Path> psFiles = findPsFiles(convertersTestDir);

        // Ensure deterministic order for stable test output
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedLPhy(psFile));
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

    private DynamicTest assertScriptMatchesExpectedLPhy(Path psPath) throws IOException {
        String source = Files.readString(psPath, StandardCharsets.UTF_8);

        // Find corresponding .lphy file
        String psFileName = psPath.getFileName().toString();
        String lphyFileName = psFileName.replace(".phylospec", ".lphy");
        Path lphyPath = psPath.getParent().resolve(lphyFileName);

        if (!Files.exists(lphyPath)) {
            return DynamicTest.dynamicTest(
                    psPath.getFileName().toString() + " (missing .lphy file)",
                    () -> {
                        throw new AssertionError("Expected LPhy file not found: " + lphyPath);
                    }
            );
        }

        String expectedLPhy = Files.readString(lphyPath, StandardCharsets.UTF_8);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            // Lex and parse the PhyloSpec file
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens);
            List<Stmt> statements = parser.parse();

            // Resolve all types
            ComponentResolver componentResolver = new ComponentResolver();
            componentResolver.registerLibraryFromFile("../../schema/phylospec-core-component-library.json");
            componentResolver.importEntireNamespace(List.of("phylospec"));
            TypeResolver resolver = new TypeResolver(componentResolver);
            for (Stmt statement : statements) {
                statement.accept(resolver);
            }

            // Convert AST to LPhy using LPhyConverter
            String actualLPhyString = LPhyConverter.convertToLphy(statements).replace("\t", "    ");
            String expectedLPhyString = expectedLPhy.trim();

            assertEquals(
                    expectedLPhyString,
                    actualLPhyString,
                    "LPhy conversion mismatch for: " + psPath
            );
        });
    }
}

