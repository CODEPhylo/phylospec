package org.phylospec.tiling.mcmc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.phylospec.tiling.errors.TileApplicationError;

public class McmcSpecTest {

    @Test
    public void samplerUsesDefaultChainLength() {
        assertEquals(10_000_000, new McmcSamplerSpec().chainLength());
        assertEquals(500, new McmcSamplerSpec(500).chainLength());
    }

    @Test
    public void loggersAcceptValidSettings() {
        new ScreenLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, null, null);
        new ScreenLoggerSpec<>(10, List.of("a", "b"), List.of("first", "second"));
        new FileLoggerSpec<>(10, "run.log", List.of("a"), null);
        new TreeLoggerSpec<>(10, "run.trees", "tree");
    }

    @Test
    public void loggersRejectMissingFiles() {
        assertFails(() -> new FileLoggerSpec<>(10, null, null, null), "no file to write to");
        assertFails(() -> new FileLoggerSpec<>(10, " ", null, null), "no file to write to");
        assertFails(() -> new TreeLoggerSpec<>(10, "", "tree"), "no file to write to");
    }

    @Test
    public void loggersRejectParameterNamesThatDoNotMatchTheParameters() {
        assertFails(
                () -> new ScreenLoggerSpec<>(10, List.of("a", "b"), List.of("first")),
                "2 parameters but 1 parameter names");
        assertFails(
                () -> new FileLoggerSpec<>(10, "run.log", List.of("a"), List.of("first", "second")),
                "1 parameters but 2 parameter names");
        assertFails(
                () -> new ScreenLoggerSpec<String>(10, null, List.of("first")), "parameter names but no parameters");
    }

    @Test
    public void loggersRejectDuplicateParameterNames() {
        assertFails(
                () -> new FileLoggerSpec<>(10, "run.log", List.of("a", "b"), List.of("same", "same")),
                "`same` is used more than once");
    }

    @Test
    public void loggersRejectParameterNamesOfTheAlwaysLoggedColumns() {
        for (String name : List.of("posterior", "prior", "likelihood")) {
            assertFails(
                    () -> new ScreenLoggerSpec<>(10, List.of("a", "b"), List.of("first", name)),
                    "clashes with the `" + name + "` column");
        }
    }

    private static void assertFails(Executable construction, String description) {
        TileApplicationError error = assertThrows(TileApplicationError.class, construction);
        assertTrue(error.getMessage().contains(description), error.getMessage());
    }
}
