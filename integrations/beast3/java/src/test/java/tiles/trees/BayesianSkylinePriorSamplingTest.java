package tiles.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import runner.PhyloSpecRunner;

/**
 * Samples the CRC09 tree prior with MCMC, without data, and compares the samples with values
 * known in closed form. The density tests check the prior at fixed states; these tests also
 * check that the operators sample the intended distribution.
 */
class BayesianSkylinePriorSamplingTest {

    private static final double EULER_GAMMA = 0.5772156649015329;

    @TempDir
    Path directory;

    private int runs;

    @BeforeEach
    void copyAlignment() throws Exception {
        // 12 taxa, used only for their names: the models below have no likelihood
        Files.copy(Path.of("src/test/java/resources/primate-mtDNA.nex"), this.directory.resolve("alignment.nex"));
    }

    @Test
    void randomCompositionVisitsEveryCompositionEquallyOften() throws Exception {
        Map<String, double[]> trace =
                sample("Vector<PositiveInteger> groups ~ RandomComposition(total=5, numParts=3)", 200_000);

        // the 6 compositions of 5 into 3 positive parts are equally likely
        Map<String, Integer> counts = new HashMap<>();
        int n = trace.get("groups.1").length;
        for (int i = 0; i < n; i++) {
            String composition = (int) trace.get("groups.1")[i] + "," + (int) trace.get("groups.2")[i] + ","
                    + (int) trace.get("groups.3")[i];
            counts.merge(composition, 1, Integer::sum);
        }
        assertEquals(6, counts.size(), counts.toString());
        counts.forEach((composition, count) -> assertEquals(1.0 / 6, (double) count / n, 0.02, composition));
    }

    @Test
    void exponentialMarkovChainHasTheExpectedLogMeans() throws Exception {
        Map<String, double[]> trace = sample("""
                PositiveReal initialMean ~ LogNormal(logMean=0.0, logSd=1.0)
                Vector<PositiveReal> sizes ~ ExponentialMarkovChain(initialMean=initialMean, numValues=3)
                """, 1_000_000);

        // sizes[k] = sizes[k-1] * E with E ~ Exponential(1) and E[log E] = -gamma,
        // so E[log sizes[k]] = E[log initialMean] - k * gamma = -k * gamma
        assertEquals(0.0, meanLog(trace.get("initialMean")), 0.15, "initialMean");
        for (int k = 1; k <= 3; k++) {
            assertEquals(-k * EULER_GAMMA, meanLog(trace.get("sizes." + k)), 0.15, "sizes." + k);
        }
    }

    @Test
    void skylineWithFixedGroupsHasTheExpectedTreeHeight() throws Exception {
        Map<String, double[]> trace = sample("""
                Alignment data = fromNexus("alignment.nex")
                Tree tree ~ SkylineCoalescent(populationSizes=[1.0, 3.0], groupSizes=[8, 3], taxa=taxa(data))
                """, 4_000_000);

        // With k lineages the waiting time is exponential with mean N / C(k, 2). The first 8
        // coalescences (12 -> 4 lineages) use N = 1, the last 3 (4 -> 1 lineage) use N = 3.
        // Swapped groups or swapped population sizes change the mean by more than 10%.
        double expected = 0.0;
        for (int k = 12; k >= 2; k--) {
            double populationSize = k > 4 ? 1.0 : 3.0;
            expected += populationSize / (k * (k - 1) / 2.0);
        }
        assertEquals(expected, mean(trace.get("tree.height")), 0.025 * expected);
    }

    @Test
    void crc09TreePriorKeepsTheMarginalPriorsOfGroupsAndPopulationSizes() throws Exception {
        Map<String, double[]> trace = sample("""
                Alignment data = fromNexus("alignment.nex")
                Vector<PositiveInteger> groupSizes ~ RandomComposition(total=11, numParts=2)
                PositiveReal initialMean ~ LogNormal(logMean=0.0, logSd=1.0)
                Vector<PositiveReal> populationSizes ~ ExponentialMarkovChain(initialMean=initialMean, numValues=2)
                Tree tree ~ SkylineCoalescent(populationSizes=populationSizes, groupSizes=groupSizes, taxa=taxa(data))
                """, 10_000_000);

        // The skyline is a proper density over trees for every choice of groups and population
        // sizes, so integrating the tree out leaves their priors unchanged: the first group size
        // is uniform on 1..10, and the population sizes keep the log means of the chain. The
        // population sizes move slowly together with the tree height, hence the longer chain
        // and the wider tolerance.
        double[] firstGroup = trace.get("groupSizes.1");
        for (int size = 1; size <= 10; size++) {
            int count = 0;
            for (double value : firstGroup) if ((int) value == size) count++;
            assertEquals(0.1, (double) count / firstGroup.length, 0.02, "groupSizes.1 = " + size);
        }
        for (int k = 1; k <= 2; k++) {
            assertEquals(-k * EULER_GAMMA, meanLog(trace.get("populationSizes." + k)), 0.2, "populationSizes." + k);
        }
    }

    /** Runs the model with a fixed seed and returns each logged column after a 10% burn-in. */
    private Map<String, double[]> sample(String model, int chainLength) throws Exception {
        String name = "prior" + (++this.runs);
        Path source = this.directory.resolve(name + ".phylospec");
        Files.writeString(source, model + """

                mcmc {
                    Integer chainLength = %d
                    Integer defaultLogEvery = %d
                    Integer randomSeed = 1
                }
                """.formatted(chainLength, chainLength / 10_000));
        new PhyloSpecRunner(source, List.of("beast2"))
                .runPhyloSpec(this.directory.resolve(name).toString());

        List<String[]> rows = Files.readAllLines(this.directory.resolve(name + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.trim().split("\\s+"))
                .toList();
        String[] columns = rows.getFirst();
        List<String[]> samples = rows.subList(1 + (rows.size() - 1) / 10, rows.size());
        Map<String, double[]> trace = new HashMap<>();
        for (int c = 0; c < columns.length; c++) {
            double[] values = new double[samples.size()];
            for (int i = 0; i < values.length; i++) values[i] = Double.parseDouble(samples.get(i)[c]);
            trace.put(columns[c], values);
        }
        return trace;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double meanLog(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += Math.log(value);
        return sum / values.length;
    }
}
