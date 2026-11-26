import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    private static final String THREE_TAXA_TREE = """
            Alignment data = fromNexus("src/test/java/resources/three-taxa.nex")
            Taxa taxa = taxa(data)
            Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
            """;

    private static final String PRIMATE_TAXA = """
            Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
            Taxa taxa = taxa(data)
            """;

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
    public void rejectsSampledTruncationBound() {
        assertRejected(
                """
                PositiveReal b ~ LogNormal(logMean=1.0, logSd=0.5)
                Real x ~ Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0, upper=b)
                """,
                "upper",
                "Truncated"
        );
    }

    @Test
    public void rejectsSampledSkylineChangeTimes() {
        assertRejected(
                PRIMATE_TAXA + """
                Vector<PositiveReal> times ~ IID(base=Exponential(rate=1.0), num=1)
                Tree tree ~ SkylineCoalescent(populationSizes=[1.0, 2.0], changeTimes=times, taxa=taxa)
                """,
                "changeTimes"
        );
    }

    @Test
    public void rejectsSampledSkylineGroupSizes() {
        assertRejected(
                PRIMATE_TAXA + """
                Vector<PositiveInteger> groups ~ IID(base=Categorical(probabilities=[0.5, 0.5]), num=2)
                Tree tree ~ SkylineCoalescent(populationSizes=[1.0, 2.0], groupSizes=groups, taxa=taxa)
                """,
                "groupSizes"
        );
    }

    @Test
    public void rejectsSampledCompoundChangeTimes() {
        assertRejected(
                """
                Vector<PositiveReal> times ~ IID(base=Exponential(rate=1.0), num=1)
                PopulationFunction a = constantPopulationFunction(populationSize=1.0)
                PopulationFunction b = constantPopulationFunction(populationSize=2.0)
                PopulationFunction f = compoundPopulationFunction(functions=[a, b], changeTimes=times)
                """,
                "changeTimes"
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

    /**
     * rootAge, mrca and age of a sampled tree change during MCMC. Where BEAST X copies a value
     * into a new parameter, these would stay at their starting value, so they are rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Real x ~ Normal(mean=rootAge(tree=tree), sd=1.0)",
            "Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=rootAge(tree=tree))",
            "Age h = rootAge(tree=tree)\nReal x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=h)",
            "PopulationFunction f = exponentialPopulationFunction(populationSize=3.0, growthRate=rootAge(tree=tree))\n"
                    + "Tree x ~ Coalescent(populationSize=f, taxa=taxa)",
            "PopulationFunction f = logisticPopulationFunction(carryingCapacity=3.0, growthRate=0.5, "
                    + "inflectionAge=mrca(clade=[\"A\", \"B\"], tree=tree))\n"
                    + "Tree x ~ Coalescent(populationSize=f, taxa=taxa)",
            "Alignment x ~ PhyloCTMC(tree=tree, qMatrix=jc69(), rate=mrca(clade=[\"A\", \"B\"], tree=tree)) observed as data"
    })
    public void rejectsValueComputedFromSampledTree(String statements) {
        assertRejected(THREE_TAXA_TREE + statements, "computed from a sampled tree");
    }

    /** The root age of a fixed tree does not change, so it can still be used as a value. */
    @Test
    public void acceptsRootAgeOfFixedTree() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Tree tree = fromNewick(newickString="((A:1.0,B:1.0):0.5,C:1.5);")
                Real x ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=rootAge(tree=tree))
                """).buildState("fixedTreeRootAge");

        Parameter x = state.stateNodesByPhyloSpecName.get("x");
        Likelihood prior = state.priorDistributions.get(x);
        x.setParameterValue(0, 1.5);
        prior.makeDirty();

        // The root age is 1.5, so x = 1.5 is shifted to 0, the mean of Normal(0, 1).
        assertEquals(-0.5 * Math.log(2.0 * Math.PI), prior.getLogLikelihood(), 1e-12);
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
