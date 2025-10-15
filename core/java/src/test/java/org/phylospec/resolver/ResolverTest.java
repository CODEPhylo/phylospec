package org.phylospec.resolver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class ResolverTest {

    @Test
    public void testSingleCharacterTokens() {

    }

    void testStatements(String source, Stmt... expectedStatements) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
