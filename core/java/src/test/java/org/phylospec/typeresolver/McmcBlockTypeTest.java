package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;

public class McmcBlockTypeTest {

    private static final String MODEL = """
            Alignment data = fromNexus("data.nex")
            Rate birthRate ~ LogNormal(logMean=1.0, logSd=0.5)
            Tree tree ~ Yule(birthRate=birthRate, taxa=taxa(data))
            Integer count = 3
            Vector<Real> values = [1.5, 2.5]
            String label = "label"
            """;

    @Test
    public void acceptsSamplerAndLoggers() throws IOException {
        resolve(MODEL + """
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
    public void acceptsDefaults() throws IOException {
        resolve("""
                mcmc {
                    Sampler sampler = mcmc()
                    Logger screenLogger = screenLogger()
                    Logger fileLogger = fileLogger(file="log.log")
                }
                """);
    }

    @Test
    public void loggedParametersAreNotTypeChecked() throws IOException {
        for (String parameter : List.of("birthRate", "count", "values", "tree", "label", "data")) {
            resolve(MODEL + "mcmc {\nLogger logger = screenLogger(parameters=[" + parameter + "])\n}");
        }
        resolve(MODEL + "mcmc {\nLogger logger = fileLogger(parameters=[birthRate, count, values, tree])\n}");
    }

    @Test
    public void loggedParametersMustExist() {
        assertFails(MODEL + "mcmc {\nLogger logger = screenLogger(parameters=[missing])\n}", "missing");
    }

    @Test
    public void parameterNamesAreTypeChecked() {
        assertFails(
                MODEL + "mcmc {\nLogger logger = screenLogger(parameters=[count], parameterNames=[count])\n}",
                "Wrong argument type");
    }

    @Test
    public void rejectsNonPositiveIntervalsAndChainLengths() {
        assertFails("mcmc {\nLogger logger = screenLogger(logEvery=0)\n}", "Wrong argument type");
        assertFails("mcmc {\nSampler sampler = mcmc(chainLength=-5)\n}", "Wrong argument type");
    }

    @Test
    public void rejectsUnknownAndMissingArguments() {
        assertFails("mcmc {\nSampler sampler = mcmc(length=10)\n}", "no argument named `length`");
        assertFails(MODEL + "mcmc {\nLogger logger = treeLogger(file=\"t.trees\")\n}", "required argument `tree`");
    }

    @Test
    public void loggerFilesAndStateFileAreOptional() throws IOException {
        resolve(MODEL + """
                mcmc {
                    Sampler sampler = mcmc(chainLength=10, stateFile="run.state.xml")
                    Logger fileLogger = fileLogger(logEvery=10)
                    Logger treeLogger = treeLogger(logEvery=10, tree=tree)
                }
                """);
    }

    @Test
    public void rejectsDeclarationsWithTheWrongType() {
        assertFails("mcmc {\nLogger logger = mcmc()\n}", "cannot be assigned");
        assertFails("mcmc {\nSampler sampler = screenLogger()\n}", "cannot be assigned");
    }

    @Test
    public void ignoresOtherBlocks() throws IOException {
        resolve("""
                Integer chainLength = 10
                beastX {
                    Real treeScaleWeight = 2.0
                }
                """);
    }

    private static void assertFails(String source, String description) {
        TypeError error = assertThrows(TypeError.class, () -> resolve(source));
        assertTrue(error.getMessage().contains(description), error.getMessage());
    }

    private static void resolve(String source) throws IOException {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        ComponentResolver componentResolver = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        new TypeResolver(componentResolver).visitStatements(statements);
    }
}
