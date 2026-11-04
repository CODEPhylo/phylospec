package runner;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScalarDistributionRunnerTest {
    @Test
    void shortPriorOnlyChainUpdatesNormalCauchyAndPoisson(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("scalar-distributions");
        String source = """
                Real normal ~ Normal(mean=0.0, sd=1.0)
                Real cauchy ~ Cauchy(location=0.0, scale=1.0)
                NonNegativeInteger count ~ Poisson(rate=2.3)
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 20261028
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));

        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("scalar-distributions");

        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int normalColumn = headers.indexOf("normal");
        int cauchyColumn = headers.indexOf("cauchy");
        int countColumn = headers.indexOf("count");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(
                normalColumn >= 0 && cauchyColumn >= 0 && countColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");

        Set<Double> normalValues = new HashSet<>();
        Set<Double> cauchyValues = new HashSet<>();
        Set<Integer> countValues = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double normal = Double.parseDouble(fields[normalColumn]);
            double cauchy = Double.parseDouble(fields[cauchyColumn]);
            int count = Integer.parseInt(fields[countColumn]);
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            assertTrue(Double.isFinite(normal));
            assertTrue(Double.isFinite(cauchy));
            assertTrue(count >= 0);
            normalValues.add(normal);
            cauchyValues.add(cauchy);
            countValues.add(count);
        }
        assertTrue(normalValues.stream().anyMatch(value -> value < 0.0));
        assertTrue(normalValues.stream().anyMatch(value -> value > 0.0));
        assertTrue(cauchyValues.stream().anyMatch(value -> value < 0.0));
        assertTrue(cauchyValues.stream().anyMatch(value -> value > 0.0));
        assertTrue(countValues.size() > 1, "Poisson must be sampled, not left at its initial value.");
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }
}
