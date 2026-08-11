package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.phylospec.FuzzingUtils;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

public class FuzzyTest {

    @Test
    public void testFuzz() throws IOException {
        Random random = new Random(0);

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        ComponentResolver componentResolver = new ComponentResolver(componentLibraries);
        TypeResolver typeResolver = new TypeResolver(componentResolver);

        for (int i = 0; i < 10000; i++) {
            String input = generateFuzzInput(random, i);
            List<Stmt> statements;

            try {
                List<Token> tokens = new Lexer(input).scanTokens();
                statements = new Parser(tokens).parse();
            } catch (Exception e) {
                fail("Parser threw an exception on iteration " + i + " (input=" + repr(input) + "): " + e);
                return;
            }

            try {
                typeResolver.visitStatements(statements);
            } catch (TypeError e) {
                // this is fine
            } catch (Exception e) {
                fail("Parser threw an exception on iteration " + i + " (input=" + repr(input) + "): " + e);
                return;
            }

            // invariant: result and every element are non-null
            assertNotNull(statements, "statements must not be null (iter=" + i + ")");
            for (Stmt stmt : statements) {
                assertNotNull(stmt, "statement must not be null (iter=" + i + ")");
            }
        }
    }

    // generates one fuzz input chosen from several strategies
    private String generateFuzzInput(Random r, int iteration) {
        // first few iterations cover deterministic edge cases
        switch (iteration) {
            case 0:
                return "";
            case 1:
                return "\n";
            case 2:
                return "// comment only";
            case 3:
                return "@";
            case 4:
                return "=";
            case 5:
                return "Real x =";
            case 6:
                return "Real x = (";
            case 7:
                return "Real x = [";
            case 8:
                return "Real<> x = 1";
            case 9:
                return "import";
        }

        int strategy = r.nextInt(5);
        switch (strategy) {
            case 0:
                // random printable ASCII — stresses error recovery
                return FuzzingUtils.randomString(r, r.nextInt(80) + 1, 32, 126);
            case 1:
                // digit-heavy — stresses number parsing interactions with the parser
                return FuzzingUtils.randomDigitHeavyString(r, r.nextInt(60) + 1);
            case 2:
                // mutated valid PhyloSpec snippets — stresses near-valid paths
                return FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(5) + 1);
            case 3:
                // multiple lines of mutated statements — stresses error recovery across lines
                return FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(3))
                        + "\n"
                        + FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(3));
            default:
                // full byte range — stresses the lexer+parser pipeline together
                return FuzzingUtils.randomString(r, r.nextInt(50) + 1, 0, 127);
        }
    }

    // fuzzes real, structurally rich models by repeatedly deleting/swapping their
    // tokens and words, on top of the occasional character-level mutation
    @Test
    public void testFuzzComplicatedModels() throws IOException {
        Random random = new Random(0);

        String[] models = {
            readFile("src/test/java/org/phylospec/parser/complicated/model1.phylospec"),
            readFile("src/test/java/org/phylospec/parser/complicated/model2.phylospec"),
        };

        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        ComponentResolver componentResolver = new ComponentResolver(componentLibraries);
        TypeResolver typeResolver = new TypeResolver(componentResolver);

        for (int i = 0; i < 10000; i++) {
            String input = mutateComplicatedModel(random, models[i % models.length]);
            List<Stmt> statements;

            try {
                List<Token> tokens = new Lexer(input).scanTokens();
                statements = new Parser(tokens).parse();
            } catch (Exception e) {
                fail("Parser threw an exception on iteration " + i + " (input=" + repr(input) + "): " + e);
                return;
            }

            try {
                typeResolver.visitStatements(statements);
            } catch (TypeError e) {
                // this is fine
            } catch (Exception e) {
                fail("Parser threw an exception on iteration " + i + " (input=" + repr(input) + "): " + e);
                return;
            }

            // invariant: result and every element are non-null
            assertNotNull(statements, "statements must not be null (iter=" + i + ")");
            for (Stmt stmt : statements) {
                assertNotNull(stmt, "statement must not be null (iter=" + i + ")");
            }
        }
    }

    // applies a random number of token-, word-, and character-level mutations in sequence,
    // so later rounds mutate the already-mutated output of earlier ones
    private String mutateComplicatedModel(Random r, String source) {
        String result = source;
        int rounds = r.nextInt(4) + 1;
        for (int i = 0; i < rounds; i++) {
            switch (r.nextInt(5)) {
                case 0:
                    result = FuzzingUtils.deleteRandomToken(r, result);
                    break;
                case 1:
                    result = FuzzingUtils.swapRandomTokens(r, result);
                    break;
                case 2:
                    result = FuzzingUtils.deleteRandomWord(r, result);
                    break;
                case 3:
                    result = FuzzingUtils.swapRandomWords(r, result);
                    break;
                default:
                    result = FuzzingUtils.mutate(r, result, 1);
            }
        }
        return result;
    }

    // reads a source file relative to the module directory
    private String readFile(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    // valid PhyloSpec statements that cover the main grammar rules
    private String pickValidStatement(Random r) {
        String[] snippets = {
            "Real x = 1.0",
            "Real x ~ LogNormal(meanLog = 0.0, sdLog = 1.0)",
            "Real x = a + b * c",
            "Real x = [1, 2, 3]",
            "Real x = [v for v in values]",
            "import phylospec.distributions",
            "@Observed() Real x ~ Normal(mu = 0.0, sigma = 1.0)",
            "Real<T> x = func(a = 1, b = 2)",
            "Real x = (a + b) * (c - d)",
            "Real x = obj.field",
            "Real x = f(a = 1,)",
            "Real x = [1, 2,]",
        };
        return snippets[r.nextInt(snippets.length)];
    }

    // returns a compact representation of a string for failure messages
    private String repr(String s) {
        if (s.length() > 60) return "\"" + s.substring(0, 60).replace("\n", "\\n") + "...\"";
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
