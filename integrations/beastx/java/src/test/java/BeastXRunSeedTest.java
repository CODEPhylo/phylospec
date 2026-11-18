import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeastXRunSeedTest {
    @TempDir Path directory;

    @Test
    void resolvesAConstantSeedBeforeApplyingTheModelTiles() throws Exception {
        Files.writeString(directory.resolve("tiny.fasta"), ">a\nACGT\n>b\nACGA\n>c\nTCGA\n");
        String source = """
                Alignment alignment = fromFasta("tiny.fasta")
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa(alignment))
                Integer seedValue = 123
                mcmc {
                    Integer randomSeed = seedValue
                }
                """;
        List<String> events = new ArrayList<>();
        var model = PhyloSpecRunner.buildXmlExportModel(directory.resolve("model.phylospec"), source,
                "example", message -> {}, path -> events.add("input " + path.getFileName()),
                seed -> events.add("seed " + seed));

        assertEquals(List.of("seed 123", "input tiny.fasta"), events);
        assertEquals(123L, model.beastState.randomSeed);
    }
}
