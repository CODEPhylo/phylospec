package org.phylospec.typeresolver.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;

/** Tests the length constraints of the core {@code taxa(names, species, ages)} generator. */
public class TaxaGeneratorConstraintTest {

    @Test
    public void testMatchingLengthsRaiseNoWarning() throws Exception {
        assertTrue(collectWarnings("Taxa t = taxa(names=[\"A\", \"B\"], species=[\"x\", \"y\"], ages=[0.0, 1.0])")
                .isEmpty());
    }

    @Test
    public void testMismatchedAgesRaiseWarning() throws Exception {
        List<Error> warnings = collectWarnings("Taxa t = taxa(names=[\"A\", \"B\"], ages=[0.0, 1.0, 2.0])");

        assertEquals(1, warnings.size(), "Expected exactly one warning, but got: " + warnings);
        assertEquals(
                "The inputs for 'taxa' might be invalid.", warnings.getFirst().description());
    }

    @Test
    public void testMismatchedSpeciesRaiseWarning() throws Exception {
        List<Error> warnings = collectWarnings("Taxa t = taxa(names=[\"A\", \"B\", \"C\"], species=[\"x\"])");

        assertEquals(1, warnings.size(), "Expected exactly one warning, but got: " + warnings);
    }

    private static List<Error> collectWarnings(String script) throws Exception {
        TypeResolver typeResolver =
                new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));

        List<Error> warnings = new ArrayList<>();
        typeResolver.registerEventListener(new ErrorEventListener() {
            @Override
            public void errorDetected(Error error) {}

            @Override
            public void warningDetected(Error warning) {
                warnings.add(warning);
            }
        });

        List<Stmt> statements = new Parser(new Lexer(script).scanTokens()).parse();
        for (Stmt statement : statements) {
            statement.accept(typeResolver);
        }

        return warnings;
    }
}
