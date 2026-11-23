package org.phylospec.tiling.mcmc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.errors.TileApplicationError;

public class McmcBlockValidatorTest {

    @Test
    public void acceptsSamplerAndLoggers() {
        validate("""
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                mcmc {
                    Sampler sampler = mcmc(chainLength=1000000)
                    Logger screenLogger = screenLogger(
                        logEvery=100, parameters=[birthRate], parameterNames=["myBirthRate"]
                    )
                    Logger fileLogger = fileLogger(file="log.log", logEvery=1000, parameters=[birthRate])
                    Logger treeLogger = treeLogger(file="trees.trees", logEvery=10000, tree)
                }
                """);
    }

    @Test
    public void acceptsDefaults() {
        validate("""
                mcmc {
                    Sampler sampler = mcmc()
                    Logger screenLogger = screenLogger()
                    Logger fileLogger = fileLogger(file="log.log")
                }
                """);
    }

    @Test
    public void acceptsModelsWithoutMcmcBlock() {
        validate("Real x ~ Normal(mean=0.0, sd=1.0)");
    }

    @Test
    public void ignoresOtherBlocks() {
        validate("""
                Integer chainLength = 10
                beastX {
                    Real treeScaleWeight = 2.0
                }
                """);
    }

    @Test
    public void rejectsSettingsThatAreNotPartOfTheBlock() {
        assertFails("mcmc {\nInteger chainLength = 1000\n}", "Only samplers and loggers", "Declare a sampler");
        assertFails("mcmc {\nInteger defaultLogEvery = 10\n}", "Only samplers and loggers", "Declare a sampler");
        assertFails("mcmc {\nInteger randomSeed = 42\n}", "Only samplers and loggers", "Declare a sampler");
        assertFails("mcmc {\nString outputPrefix = \"run\"\n}", "Only samplers and loggers", "Declare a sampler");
        assertFails("mcmc {\nReal treeScaleWeight = 2.0\n}", "Only samplers and loggers", "block of that engine");
        assertFails("mcmc {\nReal x ~ Normal(mean=0.0, sd=1.0)\n}", "Only samplers and loggers", "Declare a sampler");
        assertFails("mcmc {\nReal x = log(2.0)\n}", "Only samplers and loggers", "Declare a sampler");
    }

    @Test
    public void rejectsUnknownArguments() {
        assertFails("mcmc {\nSampler sampler = mcmc(length=10)\n}", "`mcmc` has no argument `length`", "`chainLength`");
        assertFails(
                "mcmc {\nLogger logger = treeLogger(file=\"t.trees\", trees=[tree])\n}",
                "`treeLogger` has no argument `trees`",
                "`tree` (required)");
    }

    @Test
    public void rejectsMissingRequiredArguments() {
        assertFails(
                "mcmc {\nLogger logger = fileLogger(logEvery=10)\n}", "needs the argument `file`", "`file` (required)");
        assertFails(
                "mcmc {\nLogger logger = treeLogger(file=\"t.trees\")\n}",
                "needs the argument `tree`",
                "`tree` (required)");
    }

    @Test
    public void rejectsDuplicateAndUnnamedArguments() {
        assertFails(
                "mcmc {\nLogger logger = screenLogger(logEvery=10, logEvery=20)\n}",
                "`logEvery` of `screenLogger` is passed more than once",
                "only once");
        assertFails("mcmc {\nLogger logger = screenLogger(10, [x])\n}", "has no name", "`logEvery`");
    }

    @Test
    public void rejectsMoreThanOneSampler() {
        assertFails("""
                mcmc {
                    Sampler first = mcmc()
                    Sampler second = mcmc(chainLength=10)
                }
                """, "more than one sampler", "Remove all but one sampler");
    }

    private static void assertFails(String source, String description, String hint) {
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> validate(source));

        assertNotNull(error.getAstNode());
        assertTrue(error.getMessage().contains(description), error.getMessage());
        assertTrue(error.getMessage().contains(hint), error.getMessage());
    }

    private static void validate(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        McmcBlockValidator.validate(statements);
    }
}
