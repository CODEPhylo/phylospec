package templatematching;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.phylospec.ast.*;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.VariableResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateMatchingScriptFilesTest {

    @TestFactory
    public Iterable<DynamicTest> testAllPhylospecFiles() throws IOException {
        List<Path> psFiles = findPsFiles(Paths.get("src/test/java/templatematching"));
        psFiles.sort(Comparator.comparing(Path::toString));

        List<DynamicTest> tests = new ArrayList<>();
        for (Path psFile : psFiles) {
            tests.add(buildTest(psFile));
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

    private DynamicTest buildTest(Path psPath) throws IOException {
        List<String> lines = Files.readAllLines(psPath, StandardCharsets.UTF_8);
        String templateSource = extractSection(lines, "TEMPLATE");
        String querySource = extractSection(lines, "QUERY");
        Map<String, String> expectedBindings = extractExpectedBindings(lines);
        boolean expectNoMatch = isNoMatch(lines);

        return DynamicTest.dynamicTest(psPath.getFileName().toString(), () -> {
            // parse and simplify the query
            List<Token> queryTokens = new Lexer(querySource).scanTokens();
            List<AstNode> queryNodes = new Parser(queryTokens).parseStmtOrExpr();
            queryNodes = simplify(queryNodes);
            AstNode queryRoot = queryNodes.getLast();
            VariableResolver queryVariableResolver = new VariableResolver(queryNodes);

            // run the template matcher
            AstTemplateMatcher matcher = new AstTemplateMatcher(templateSource);
            Map<String, AstNode> result = matcher.match(queryRoot, queryVariableResolver);

            if (expectNoMatch) {
                assertNull(result, "expected no match but got bindings: " + result);
            } else {
                assertNotNull(result, "expected a match but the matcher returned no match");

                AstPrinter printer = new AstPrinter();
                Map<String, String> actualBindings = new LinkedHashMap<>();
                for (Map.Entry<String, AstNode> entry : result.entrySet()) {
                    actualBindings.put(entry.getKey(), printNode(entry.getValue(), printer));
                }

                assertEquals(expectedBindings, actualBindings, "variable bindings do not match for: " + psPath);
            }
        });
    }

    // applies RemoveGroupings and EvaluateLiterals to a mixed list of Stmt/Expr nodes
    private List<AstNode> simplify(List<AstNode> nodes) {
        List<Stmt> stmts = nodes.stream()
                .filter(n -> n instanceof Stmt)
                .map(n -> (Stmt) n)
                .collect(Collectors.toList());

        if (stmts.size() == nodes.size()) {
            stmts = new RemoveGroupings().transform(stmts);
            stmts = new EvaluateLiterals().transform(stmts);
            return new ArrayList<>(stmts);
        }

        RemoveGroupings removeGroupings = new RemoveGroupings();
        EvaluateLiterals evaluateLiterals = new EvaluateLiterals();
        return nodes.stream().map(node -> {
            if (node instanceof Expr expr) {
                return (AstNode) expr.accept(removeGroupings).accept(evaluateLiterals);
            }
            return node;
        }).collect(Collectors.toList());
    }

    private String printNode(AstNode node, AstPrinter printer) {
        if (node instanceof Expr expr) return expr.accept(printer);
        if (node instanceof Stmt stmt) return stmt.accept(printer);
        if (node instanceof AstType type) return type.accept(printer);
        throw new IllegalArgumentException("unknown AstNode type: " + node.getClass());
    }

    // extracts the source lines between the two // <marker> guards
    private String extractSection(List<String> lines, String marker) {
        String guard = "// " + marker;
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(guard)) {
                if (start == -1) {
                    start = i + 1;
                } else {
                    return String.join("\n", lines.subList(start, i));
                }
            }
        }
        throw new IllegalArgumentException("section " + marker + " not found or not closed");
    }

    // returns true if the MATCH section contains only // NO MATCH
    private boolean isNoMatch(List<String> lines) {
        String guard = "// MATCH";
        boolean inMatch = false;
        for (String line : lines) {
            if (line.trim().equals(guard)) {
                if (!inMatch) {
                    inMatch = true;
                    continue;
                } else {
                    break;
                }
            }
            if (inMatch && line.trim().equals("// NO MATCH")) {
                return true;
            }
        }
        return false;
    }

    // parses the MATCH section into a map of "$varname" -> "printed ast"
    private Map<String, String> extractExpectedBindings(List<String> lines) {
        String guard = "// MATCH";
        Map<String, String> bindings = new LinkedHashMap<>();
        boolean inMatch = false;

        for (String line : lines) {
            if (line.trim().equals(guard)) {
                if (!inMatch) {
                    inMatch = true;
                    continue;
                } else {
                    break;
                }
            }
            if (!inMatch) continue;

            String trimmed = line.trim();
            if (!trimmed.startsWith("//")) continue;

            String content = trimmed.substring(2).stripLeading();
            if (content.equals("NO MATCH")) continue;

            int arrowIdx = content.indexOf(" -> ");
            if (arrowIdx == -1) continue;

            String varName = content.substring(0, arrowIdx).trim();
            String value = content.substring(arrowIdx + 4).trim();
            bindings.put(varName, value);
        }

        return bindings;
    }
}
