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

class UniformRunnerTest {
    @Test
    void shortPriorOnlyChainSamplesBothUniformParameters(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("uniform");
        String source = """
                Real x ~ Uniform(lower=-1.0, upper=1.0)
                Integer n ~ DiscreteUniform(lower=1, upper=10)
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 20261026
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));

        // Source failures propagate as exceptions and fail the test directly.
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("uniform");

        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int xColumn = headers.indexOf("x");
        int nColumn = headers.indexOf("n");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(xColumn >= 0 && nColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(202, lines.size(), "Header plus samples 0 through 200.");

        Set<Double> realValues = new HashSet<>();
        Set<Integer> integerValues = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double x = Double.parseDouble(fields[xColumn]);
            int n = Integer.parseInt(fields[nColumn]);
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            assertTrue(x >= -1.0 && x <= 1.0);
            assertTrue(n >= 1 && n <= 10);
            realValues.add(x);
            integerValues.add(n);
        }
        assertTrue(realValues.size() > 1);
        assertTrue(realValues.stream().anyMatch(value -> value < 0.0));
        assertTrue(realValues.stream().anyMatch(value -> value > 0.0));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), integerValues);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }
}
