package org.phylospec.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.AstPrinter;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.ParsedType;
import org.phylospec.components.ParsedTypeProperty;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.typeresolver.ResolvedType;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;

public class ScriptFilesParserTest {

    /**
     * Goes through every .phylospec file in the test folder, parses it, and compares
     * the obtained AST tree and resolved types with the expectations specified in
     * the PhyloSpec files as comments.
     */
    @TestFactory
    public Iterable<DynamicTest> testAllPsScriptsAgainstExpectedAst() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/org/phylospec/parser"));

        // Ensure deterministic order for stable test output
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(assertScriptMatchesExpectedAst(psFile));
        }
        return tests;
    }

    private List<Path> findPsFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".phylospec"))
                    .collect(Collectors.toList());
        }
    }

    private DynamicTest assertScriptMatchesExpectedAst(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);

        List<String> expectedAstLines = extractExpectedAstLines(lines);
        Optional<List<String>> expectedTypeLines = extractExpectedTypeLines(lines);

        String source = String.join("\n", lines);

        return DynamicTest.dynamicTest(
                psPath.getFileName().toString(),
                () -> {
                    Lexer lexer = new Lexer(source);
                    List<Token> tokens = lexer.scanTokens();
                    Parser parser = new Parser(tokens);
                    List<Stmt> statements = parser.parse();

                    AstPrinter printer = new AstPrinter();
                    List<String> actualAstLines = new ArrayList<>();
                    for (Stmt statement : statements) {
                        actualAstLines.add(statement.accept(printer));
                    }

                    assertEquals(
                            expectedAstLines.size(),
                            actualAstLines.size(),
                            "Wrong number of AST lines for: " + psPath);

                    for (int i = 0; i < expectedAstLines.size(); i++) {
                        String expected = expectedAstLines.get(i).trim();
                        String actual = actualAstLines.get(i).trim();
                        assertEquals(
                                expected, actual, "AST mismatch at index " + i + " for: " + psPath);
                    }

                    if (expectedTypeLines.isPresent()) {
                        assertResolvedTypesMatch(expectedTypeLines.get(), statements, psPath);
                    }
                });
    }

    private void assertResolvedTypesMatch(
            List<String> expectedTypeLines, List<Stmt> statements, Path psPath) throws IOException {
        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        ComponentResolver componentResolver = new ComponentResolver(componentLibraries);
        TypeResolver resolver = new TypeResolver(componentResolver);
        List<ResolvedType> actualTypes = new ArrayList<>();

        for (Stmt statement : statements) {
            try {
                Set<ResolvedType> statementTypes = statement.accept(resolver);
                if (hasResolvedValue(statement, resolver)) {
                    actualTypes.addAll(
                            statementTypes.stream()
                                    .sorted(Comparator.comparing(this::formatResolvedType))
                                    .toList());
                }
            } catch (TypeError ignored) {
                // type errors are checked by ScriptFilesTypesTest

                Set<ResolvedType> variableTypes = recoverResolvedTypes(statement, resolver);
                actualTypes.addAll(
                        variableTypes.stream()
                                .sorted(Comparator.comparing(this::formatResolvedType))
                                .toList());
            }
        }

        assertEquals(
                expectedTypeLines.size(),
                actualTypes.size(),
                "Wrong number of resolved types for: " + psPath);

        for (int index = 0; index < expectedTypeLines.size(); index++) {
            assertResolvedTypeMatches(
                    new ParsedType(expectedTypeLines.get(index).trim()),
                    actualTypes.get(index),
                    "type at index " + index + " for: " + psPath);
        }
    }

    private void assertResolvedTypeMatches(
            ParsedType expected, ResolvedType actual, String location) {
        String expectedName =
                expected.getNamespace().isEmpty()
                        ? expected.getAtomicTypeName()
                        : expected.stripGenerics();
        String actualName =
                expected.getNamespace().isEmpty() ? actual.getUnqualifiedName() : actual.getName();
        assertEquals(expectedName, actualName, "Type name mismatch for " + location);

        List<ParsedType> expectedParameters = expected.getTypeParameters();
        if (!expectedParameters.isEmpty()) {
            List<String> actualParameterNames = actual.getParametersNames();
            assertEquals(
                    expectedParameters.size(),
                    actualParameterNames.size(),
                    "Type parameter count mismatch for " + location);

            for (int index = 0; index < expectedParameters.size(); index++) {
                String parameterName = actualParameterNames.get(index);
                assertTrue(
                        actual.getParameterTypes().containsKey(parameterName),
                        "Missing resolved type parameter '" + parameterName + "' for " + location);
                assertResolvedTypeMatches(
                        expectedParameters.get(index),
                        actual.getParameterTypes().get(parameterName),
                        "parameter '" + parameterName + "' of " + location);
            }
        }

        for (ParsedTypeProperty expectedProperty : expected.getTypeProperties()) {
            assertTrue(
                    expectedProperty instanceof ParsedTypeProperty.Constant,
                    "Expected type properties must have constant values for " + location);
            ParsedTypeProperty.Constant constant = (ParsedTypeProperty.Constant) expectedProperty;
            assertEquals(
                    constant.getValue(),
                    String.valueOf(actual.properties().get(constant.getPropertyName())),
                    "Type property '" + constant.getPropertyName() + "' mismatch for " + location);
        }
    }

    private Set<ResolvedType> recoverResolvedTypes(Stmt statement, TypeResolver resolver) {
        if (statement instanceof Stmt.ObservedAs observedAs) {
            return resolver.resolveTypeSet(observedAs.observedAs);
        }
        return resolver.resolveVariable(statement.getName());
    }

    private boolean hasResolvedValue(Stmt statement, TypeResolver resolver) {
        if (statement instanceof Stmt.Assignment assignment) {
            return !resolver.resolveTypeSet(assignment.expression).isEmpty();
        }
        if (statement instanceof Stmt.Draw draw) {
            return !resolver.resolveTypeSet(draw.expression).isEmpty();
        }
        if (statement instanceof Stmt.Decorated decorated) {
            return hasResolvedValue(decorated.statement, resolver);
        }
        return true;
    }

    private String formatResolvedType(ResolvedType type) {
        List<String> parameters = new ArrayList<>();
        for (String parameterName : type.getParametersNames()) {
            ResolvedType parameterType = type.getParameterTypes().get(parameterName);
            parameters.add(
                    parameterType == null ? parameterName : formatResolvedType(parameterType));
        }

        List<String> properties =
                type.properties().getPropertyNames().stream()
                        .sorted()
                        .map(name -> name + "=" + type.properties().get(name))
                        .toList();
        if (parameters.isEmpty() && properties.isEmpty()) {
            return type.getUnqualifiedName();
        }

        String parameterContent = String.join(", ", parameters);
        String propertyContent = properties.isEmpty() ? "" : "; " + String.join(", ", properties);
        return type.getUnqualifiedName() + "<" + parameterContent + propertyContent + ">";
    }

    private Optional<List<String>> extractExpectedTypeLines(List<String> lines) {
        int expectStart = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).trim().startsWith("// EXPECT TYPES")) {
                expectStart = index + 1;
                break;
            }
        }

        if (expectStart == -1) {
            return Optional.empty();
        }

        List<String> expected = new ArrayList<>();
        for (int index = expectStart; index < lines.size(); index++) {
            String raw = lines.get(index);
            String trimmed = raw.trim();
            if (trimmed.startsWith("// EXPECT TYPES")) {
                break;
            }
            if (!trimmed.startsWith("//")) {
                break;
            }

            int commentStart = raw.indexOf("//");
            String content = raw.substring(commentStart + 2);
            if (!content.isEmpty() && content.charAt(0) == ' ') {
                content = content.substring(1);
            }
            expected.add(content);
        }
        return Optional.of(expected);
    }

    private List<String> extractExpectedAstLines(List<String> lines) {
        List<String> expected = new ArrayList<>();

        int expectStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("// EXPECT AST")) {
                expectStart = i + 1;
                break;
            }
        }

        int expectEnd = -1;
        for (int i = expectStart + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("// EXPECT AST")) {
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
