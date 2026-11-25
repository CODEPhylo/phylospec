package org.phylospec.tiling.mcmc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.errors.TileApplicationError;

class McmcBlockValidatorTest {

    @Test
    void acceptsOneSamplerAndLoggers() {
        McmcBlockValidator.validate(parse("""
                Real x ~ Normal(mean=0.0, sd=1.0)
                mcmc {
                    Sampler sampler = mcmc(chainLength=10)
                    Logger fileLogger = fileLogger(file="x.log", logEvery=1, parameters=[x])
                }
                """));
    }

    @Test
    void rejectsASecondSamplerAndPointsAtIt() {
        List<Stmt> statements = parse("""
                mcmc {
                    Sampler sampler = mcmc(chainLength=10)
                    Sampler other = mcmc(chainLength=20)
                }
                """);

        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> McmcBlockValidator.validate(statements));
        assertSame(statements.get(1), error.getAstNode());
        assertTrue(error.getMessage().contains("only have one sampler"), error.getMessage());
    }

    @Test
    void rejectsFormerSettings() {
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> McmcBlockValidator.validate(parse("""
                        mcmc {
                            Integer chainLength = 10
                        }
                        """)));
        assertTrue(error.getMessage().contains("mcmc(chainLength=10)"), error.getMessage());
    }

    @Test
    void rejectsEveryOtherStatementAndPointsAtIt() {
        for (String statement :
                List.of("Real logEvery = 2.5", "Real other = 1.0", "Real x ~ Normal(mean=0.0, sd=1.0)")) {
            List<Stmt> statements =
                    parse("mcmc {\n    Sampler sampler = mcmc(chainLength=10)\n    " + statement + "\n}");

            TileApplicationError error =
                    assertThrows(TileApplicationError.class, () -> McmcBlockValidator.validate(statements));
            assertSame(statements.get(1), error.getAstNode(), statement);
        }
    }

    @Test
    void pointsLoggerSettingsToTheLoggers() {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> McmcBlockValidator.validate(parse("mcmc {\n    Real logEvery = 2.5\n}")));
        assertTrue(error.getMessage().contains("fileLogger(logEvery=2.5)"), error.getMessage());
    }

    private static List<Stmt> parse(String source) {
        return new Parser(new Lexer(source).scanTokens()).parse();
    }
}
