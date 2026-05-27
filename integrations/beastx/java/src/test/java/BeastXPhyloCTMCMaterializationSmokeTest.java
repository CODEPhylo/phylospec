import dr.evomodel.treelikelihood.BeagleTreeLikelihood;
import dr.inference.model.Likelihood;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXPhyloCTMCLikelihoodSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXPhyloCTMCMaterializationSmokeTest {

    private static final Path MODEL_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "tiling",
                    "phyloctmc",
                    "mostBasic.phylospec"
            );

    @Test
    public void defaultModelKeepsPhyloCTMCAsSpecification() throws Exception {
        String source =
                readSource(MODEL_PATH);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        assertEquals(1, model.beastState.likelihoodDistributions.size());

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        assertInstanceOf(BeastXPhyloCTMCLikelihoodSpec.class, likelihood);
        assertEquals("alignment_likelihood", likelihood.getId());
    }

    @Test
    public void materializedModelBuildsBeagleTreeLikelihood() throws Exception {
        String source =
                readSource(MODEL_PATH);

        BeastXModel model =
                assumeMaterializedModelCanBeBuilt(source);

        assertEquals(1, model.beastState.likelihoodDistributions.size());

        Likelihood likelihood =
                model.beastState.likelihoodDistributions.getFirst();

        assertInstanceOf(BeagleTreeLikelihood.class, likelihood);
        assertEquals("alignment_likelihood", likelihood.getId());
        assertFalse(likelihood instanceof BeastXPhyloCTMCLikelihoodSpec);
    }

    @Test
    public void materializedPhyloCTMCLikelihoodCanBeEvaluated() throws Exception {
        String source =
                readSource(MODEL_PATH);

        BeastXModel model =
                assumeMaterializedModelCanBeBuilt(source);

        double logLikelihood =
                model.likelihood.getLogLikelihood();

        double logPosterior =
                model.posterior.getLogLikelihood();

        assertTrue(
                Double.isFinite(logLikelihood),
                "Expected materialized PhyloCTMC likelihood to evaluate to a finite value."
        );

        assertTrue(
                Double.isFinite(logPosterior),
                "Expected posterior with materialized PhyloCTMC likelihood to evaluate to a finite value."
        );
    }

    private BeastXModel assumeMaterializedModelCanBeBuilt(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        try {
            return runner.buildMaterializedModel("test");
        } catch (RuntimeException error) {
            String message =
                    error.getMessage();

            if (message != null && message.contains("No acceptable BEAGLE library plugins found")) {
                Assumptions.abort(
                        "Skipping PhyloCTMC materialization smoke test because BEAGLE native library is not available."
                );
            }

            throw error;
        }
    }

    private String readSource(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }
}