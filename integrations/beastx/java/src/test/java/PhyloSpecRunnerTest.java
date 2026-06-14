import dr.inference.mcmc.MCMC;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.operators.DeltaExchangeOperator;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.ScaleOperator;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.mcmc.MCMCBuilder;
import tiling.operators.OperatorBuilder;
import tiling.BeastXState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhyloSpecRunnerTest {

    private static final double TOLERANCE = 1e-10;

    @Test
    public void buildsStateForMostBasicPhyloCTMC() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/mostBasic.phylospec");

        BeastXState beastState =
                model.beastState;

        assertEquals(0, beastState.stateNodes.size());
        assertEquals(0, beastState.priorDistributions.size());
        assertEquals(1, beastState.treePriorDistributions.size());
        assertEquals(1, beastState.likelihoodDistributions.size());
    }

    @Test
    public void buildsCompoundLikelihoodsForMostBasicPhyloCTMC() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/mostBasic.phylospec");

        assertNotNull(model.beastState);
        assertNotNull(model.prior);
        assertNotNull(model.likelihood);
        assertNotNull(model.posterior);

        assertEquals("prior", model.prior.getId());
        assertEquals("likelihood", model.likelihood.getId());
        assertEquals("posterior", model.posterior.getId());

        assertEquals(1, model.prior.getLikelihoodCount());
        assertEquals(1, model.likelihood.getLikelihoodCount());
        assertEquals(2, model.posterior.getLikelihoodCount());
    }

    @Test
    public void priorOnlyPosteriorEqualsPrior() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                """;

        BeastXModel model =
                buildModelFromSource(source);

        BeastXState beastState =
                model.beastState;

        assertEquals(1, beastState.stateNodes.size());
        assertEquals(1, beastState.priorDistributions.size());
        assertEquals(0, beastState.treePriorDistributions.size());
        assertEquals(0, beastState.likelihoodDistributions.size());

        assertEquals(1, model.prior.getLikelihoodCount());
        assertEquals(0, model.likelihood.getLikelihoodCount());
        assertEquals(2, model.posterior.getLikelihoodCount());

        assertPriorOnlyPosterior(model);
    }

    @Test
    public void multipleIndependentPriorsEvaluateAsJointPrior() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)
                Real z ~ Beta(alpha=2.0, beta=5.0)
                """;

        BeastXModel model =
                buildModelFromSource(source);

        BeastXState beastState =
                model.beastState;

        assertEquals(3, beastState.stateNodes.size());
        assertEquals(3, beastState.priorDistributions.size());
        assertEquals(0, beastState.treePriorDistributions.size());
        assertEquals(0, beastState.likelihoodDistributions.size());

        assertEquals(3, model.prior.getLikelihoodCount());
        assertEquals(0, model.likelihood.getLikelihoodCount());
        assertEquals(2, model.posterior.getLikelihoodCount());

        assertPriorOnlyPosterior(model);
    }

    @Test
    public void yuleTreePriorIsPartOfPriorNotLikelihood() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                """;

        BeastXModel model =
                buildModelFromSource(source);

        BeastXState beastState =
                model.beastState;

        assertEquals(0, beastState.stateNodes.size());
        assertEquals(0, beastState.priorDistributions.size());
        assertEquals(1, beastState.treePriorDistributions.size());
        assertEquals(0, beastState.likelihoodDistributions.size());

        assertEquals(Set.of("tree_prior"), likelihoodIds(model.prior));
        assertEquals(Set.of(), likelihoodIds(model.likelihood));

        assertPriorOnlyPosterior(model);
    }

    @Test
    public void phyloCTMCAddsAlignmentLikelihood() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/mostBasic.phylospec");

        BeastXState beastState =
                model.beastState;

        assertEquals(0, beastState.stateNodes.size());
        assertEquals(0, beastState.priorDistributions.size());
        assertEquals(1, beastState.treePriorDistributions.size());
        assertEquals(1, beastState.likelihoodDistributions.size());

        assertEquals(Set.of("tree_prior"), likelihoodIds(model.prior));
        assertEquals(Set.of("alignment_likelihood"), likelihoodIds(model.likelihood));
        assertEquals(Set.of("prior", "likelihood"), likelihoodIds(model.posterior));
    }

    @Test
    public void phyloCTMCWithInlinePriorsAddsParameterPriorsAndAlignmentLikelihood() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/withInlinePriors.phylospec");

        BeastXState beastState =
                model.beastState;

        assertEquals(2, beastState.stateNodes.size());
        assertEquals(2, beastState.priorDistributions.size());
        assertEquals(1, beastState.treePriorDistributions.size());
        assertEquals(1, beastState.likelihoodDistributions.size());

        assertEquals(
                Set.of("kappa_prior", "baseFrequencies_prior", "tree_prior"),
                likelihoodIds(model.prior)
        );

        assertEquals(Set.of("alignment_likelihood"), likelihoodIds(model.likelihood));
        assertEquals(Set.of("prior", "likelihood"), likelihoodIds(model.posterior));
    }

    @Test
    public void phyloCTMCWithStrictClockBuildsExpectedModelStructure() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/withStrictClock.phylospec");

        BeastXState beastState =
                model.beastState;

        assertEquals(0, beastState.stateNodes.size());
        assertEquals(0, beastState.priorDistributions.size());
        assertEquals(1, beastState.treePriorDistributions.size());
        assertEquals(1, beastState.likelihoodDistributions.size());

        assertEquals(Set.of("tree_prior"), likelihoodIds(model.prior));
        assertEquals(Set.of("alignment_likelihood"), likelihoodIds(model.likelihood));
        assertEquals(Set.of("prior", "likelihood"), likelihoodIds(model.posterior));
    }

    @Test
    public void importedTreePhyloCTMCHasLikelihoodButNoTreePrior() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/fromTreeMostBasic.phylospec");

        BeastXState beastState =
                model.beastState;

        assertEquals(0, beastState.stateNodes.size());
        assertEquals(0, beastState.priorDistributions.size());
        assertEquals(0, beastState.treePriorDistributions.size());
        assertEquals(1, beastState.likelihoodDistributions.size());

        assertEquals(Set.of(), likelihoodIds(model.prior));
        assertEquals(Set.of("alignment_likelihood"), likelihoodIds(model.likelihood));
        assertEquals(Set.of("prior", "likelihood"), likelihoodIds(model.posterior));
    }

    @Test
    public void runnerErrorsAreReportedAsExceptions() throws Exception {
        String source =
                readSource("src/test/java/tiling/functions/rangeEmpty.phylospec");

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        PhyloSpecRunnerException error =
                assertThrows(
                        PhyloSpecRunnerException.class,
                        () -> runner.buildState("test")
                );

        assertNotNull(error.getMessage());
        assertTrue(!error.getMessage().isBlank());
    }

    @Test
    public void rejectsStrictClockWithNonRateClockRate() {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)

                Vector<Rate> branchRates ~ StrictClock(
                    clockRate="fast",
                    tree=tree
                )
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        PhyloSpecRunnerException error =
                assertThrows(
                        PhyloSpecRunnerException.class,
                        () -> runner.buildModel("test")
                );

        assertTrue(error.getMessage().contains("StrictClock"));
        assertTrue(error.getMessage().contains("clockRate"));
    }

    @Test
    public void rejectsYuleWithNonRateBirthRate() {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate="fast",
                    taxa=taxa
                )
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        PhyloSpecRunnerException error =
                assertThrows(
                        PhyloSpecRunnerException.class,
                        () -> runner.buildModel("test")
                );

        assertTrue(error.getMessage().contains("Yule"));
        assertTrue(error.getMessage().contains("birthRate"));
    }

    @Test
    public void buildsMCMCObjectForPriorOnlyModel() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                """;

        BeastXModel model =
                buildModelFromSource(source);

        MCMC mcmc =
                new MCMCBuilder(1).build(model);

        assertEquals("mcmc", mcmc.getId());
        assertEquals(1, mcmc.getChainLength());
        assertEquals(model.posterior, mcmc.getLikelihood());
        assertEquals(1, mcmc.getOperatorSchedule().getOperatorCount());
        assertEquals(0, mcmc.getLoggers().length);
    }

    @Test
    public void runnerBuildsMCMCForPriorOnlyModel() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=0.0, sd=1.0)
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        MCMC mcmc =
                runner.buildMCMC("test", 1);

        assertEquals("mcmc", mcmc.getId());
        assertEquals(1, mcmc.getChainLength());
        assertEquals(1, mcmc.getOperatorSchedule().getOperatorCount());
        assertEquals(0, mcmc.getLoggers().length);
    }

    @Test
    public void runnerBuildMCMCRejectsUnmaterializedPhyloCTMC() throws Exception {
        String source =
                readSource("src/test/java/tiling/phyloctmc/mostBasic.phylospec");

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> runner.buildMCMC("test", 1)
                );

        assertTrue(error.getMessage().contains("unmaterialized PhyloCTMC likelihood"));
    }

    private BeastXModel buildModelFromFile(String path) throws Exception {
        return buildModelFromSource(readSource(path));
    }

    private BeastXModel buildModelFromSource(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildModel("test");
    }

    private BeastXState buildStateFromSource(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildState("test");
    }

    private void assertPriorOnlyPosterior(BeastXModel model) {
        double logPrior =
                model.prior.getLogLikelihood();

        double logLikelihood =
                model.likelihood.getLogLikelihood();

        double logPosterior =
                model.posterior.getLogLikelihood();

        assertTrue(Double.isFinite(logPrior));
        assertTrue(Double.isFinite(logLikelihood));
        assertTrue(Double.isFinite(logPosterior));

        assertEquals(0.0, logLikelihood, TOLERANCE);
        assertEquals(logPrior, logPosterior, TOLERANCE);
    }

    private Set<String> likelihoodIds(CompoundLikelihood likelihood) {
        return IntStream.range(0, likelihood.getLikelihoodCount())
                .mapToObj(likelihood::getLikelihood)
                .map(Likelihood::getId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String readSource(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }
}