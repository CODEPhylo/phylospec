package runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PhyloSpecCliTest {

    @Test
    public void displaysHelpWithoutRequiringAModel() {
        assertDoesNotThrow(() -> PhyloSpecCli.execute(new String[] {"--help"}));
    }

    @Test
    public void requiresSourceFile() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {}));

        assertEquals("A PhyloSpec source file is required.", exception.getMessage());
    }

    @Test
    public void requiresTileLibrary(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("model.phylospec");
        Files.writeString(source, "");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {source.toString()}));

        assertEquals("At least one --library must be supplied.", exception.getMessage());
    }

    @Test
    public void rejectsUnknownOption() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> PhyloSpecCli.execute(new String[] {"--unknown"}));

        assertEquals("Unknown option: --unknown", exception.getMessage());
    }
}
