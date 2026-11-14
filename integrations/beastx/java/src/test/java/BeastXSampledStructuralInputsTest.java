import org.junit.jupiter.api.Test;
import tiling.BeastXState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Some values are read once while the BEAST X model is built: an index picks one element,
 * a range fixes how many elements a loop creates, and a few bounds and category counts are
 * copied into BEAST X objects. A sampled value in these places would stay at its starting
 * value during MCMC, so these models must be rejected with a clear error.
 */
public class BeastXSampledStructuralInputsTest {

    private static final String PRIMATE_TREE = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            """;

    @Test
    public void rejectsSampledIndex() {
        assertRejected(
                """
                Probability ps[i] ~ Beta(alpha=1.0, beta=1.0) for i in 1:3
                PositiveInteger z ~ Categorical(probabilities=[0.1, 0.2, 0.7])
                Boolean b ~ Bernoulli(p=ps[z])
                """,
                "index"
        );
    }

    @Test
    public void rejectsSampledRangeBound() {
        assertRejected(
                """
                Count n ~ Binomial(numTrials=5, p=0.5)
                Real xs[i] ~ Normal(mean=0.0, sd=1.0) for i in 1:n
                """,
                "to"
        );
    }

    @Test
    public void rejectsSampledDiscreteUniformBound() {
        assertRejected(
                """
                Integer k ~ DiscreteUniform(lower=5, upper=20)
                Integer x ~ DiscreteUniform(lower=0, upper=k)
                """,
                "upper",
                "DiscreteUniform"
        );
    }

    @Test
    public void rejectsSampledRootAgeCalibrationBound() {
        assertRejected(
                PRIMATE_TREE + """
                PositiveReal b ~ LogNormal(logMean=2.0, logSd=0.5)
                Age root = rootAge(tree=tree) observed between [0.0, b]
                """,
                "to"
        );
    }

    @Test
    public void rejectsSampledMrcaCalibrationBound() {
        assertRejected(
                PRIMATE_TREE + """
                PositiveReal b ~ LogNormal(logMean=1.0, logSd=0.5)
                Age h = mrca(clade=["Homo_sapiens", "Pan"], tree=tree) observed between [0.0, b]
                """,
                "to"
        );
    }

    @Test
    public void rejectsSampledNumberOfRateCategories() {
        assertRejected(
                PRIMATE_TREE + """
                PositiveInteger c ~ Categorical(probabilities=[0.25, 0.25, 0.25, 0.25])
                Vector<Rate> siteRates ~ DiscreteGammaInv(shape=0.5, numCategories=c, numSites=numSites(data))
                """,
                "numCategories",
                "DiscreteGammaInv"
        );
    }

    /** The loop variable is deterministic, so indexing inside a loop must still work. */
    @Test
    public void acceptsLoopVariableAsIndex() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Vector<Real> means = [1.0, 2.0, 3.0]
                Real x[i] ~ Normal(mean=means[i], sd=1.0) for i in 1:3
                """).buildState("loopIndex");

        assertEquals(3, state.stateNodesByPhyloSpecName.keySet().stream().filter(name -> name.startsWith("x")).count());
    }

    /** A fixed index is not sampled, so it must still work. */
    @Test
    public void acceptsFixedIndex() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Probability ps[i] ~ Beta(alpha=1.0, beta=1.0) for i in 1:3
                Boolean b ~ Bernoulli(p=ps[2])
                """).buildState("fixedIndex");

        assertTrue(state.stateNodesByPhyloSpecName.containsKey("b"));
    }

    private static void assertRejected(String source, String... expectedMessageParts) {
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> new PhyloSpecRunner(source).buildState("sampledStructuralInputs")
        );

        for (String expectedMessagePart : expectedMessageParts) {
            assertTrue(
                    exception.getMessage().contains(expectedMessagePart),
                    "Expected error message to contain: " + expectedMessagePart
                            + "\n\nActual message:\n" + exception.getMessage()
            );
        }
    }
}
