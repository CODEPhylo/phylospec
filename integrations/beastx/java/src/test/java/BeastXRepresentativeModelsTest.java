import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXRepresentativeModelsTest {

    private static final Path REPRESENTATIVE_MODEL_DIR =
            Path.of("src", "test", "java", "tiling", "representative");

    @Test
    public void representativeModelsBuildBeastXModelSummaries() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        assertTrue(
                modelPaths.size() >= 6,
                "Expected at least 6 representative BeastX models."
        );

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            assertTrue(
                    hasPhylogeneticContent(summary),
                    "Representative model should produce at least one phylogenetic component: " + modelPath
            );

            assertNoBlankValues(summary.stateNodes, "state nodes", modelPath);
            assertNoBlankValues(summary.parameterPriors, "parameter priors", modelPath);
            assertNoBlankValues(summary.treeModels, "tree models", modelPath);
            assertNoBlankValues(summary.treePriors, "tree priors", modelPath);
            assertNoBlankValues(summary.likelihoods, "likelihoods", modelPath);
            assertNoBlankValues(summary.operators, "operators", modelPath);

            if (!summary.treePriors.isEmpty()) {
                assertTrue(
                        summary.operators.contains("NodeHeightScaleOperator"),
                        "Stochastic tree model should have a tree height operator: " + modelPath
                );

                assertTrue(
                        summary.operators.contains("ExchangeOperator"),
                        "Stochastic tree model should have an exchange operator: " + modelPath
                );

                assertTrue(
                        summary.operators.contains("WilsonBalding"),
                        "Stochastic tree model should have a Wilson-Balding operator: " + modelPath
                );
            }
        }
    }

    @Test
    public void representativeModelsCoverMultipleModelAxes() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        int modelsWithStateNodes =
                0;

        int modelsWithParameterPriors =
                0;

        int modelsWithTreePriors =
                0;

        int modelsWithLikelihoods =
                0;

        int modelsWithOperators =
                0;

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            if (!summary.stateNodes.isEmpty()) {
                modelsWithStateNodes++;
            }

            if (!summary.parameterPriors.isEmpty()) {
                modelsWithParameterPriors++;
            }

            if (!summary.treePriors.isEmpty()) {
                modelsWithTreePriors++;
            }

            if (!summary.likelihoods.isEmpty()) {
                modelsWithLikelihoods++;
            }

            if (!summary.operators.isEmpty()) {
                modelsWithOperators++;
            }
        }

        assertTrue(
                modelsWithStateNodes >= 4,
                "Representative models should include several stochastic parameter examples."
        );

        assertTrue(
                modelsWithParameterPriors >= 4,
                "Representative models should include several parameter-prior examples."
        );

        assertTrue(
                modelsWithTreePriors >= 4,
                "Representative models should include several stochastic tree-prior examples."
        );

        assertTrue(
                modelsWithLikelihoods >= 4,
                "Representative models should include several PhyloCTMC likelihood examples."
        );

        assertTrue(
                modelsWithOperators >= 4,
                "Representative models should include several models with MCMC operators."
        );
    }

    @Test
    public void representativeModelsPrintSummariesForInspection() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            System.out.println(
                    summary.toReportString(
                            "Representative model: " + modelPath.getFileName()
                    )
            );
        }
    }

    private List<Path> representativeModelPaths() throws Exception {
        assertTrue(
                Files.isDirectory(REPRESENTATIVE_MODEL_DIR),
                "Representative model directory does not exist: " + REPRESENTATIVE_MODEL_DIR
        );

        try (Stream<Path> paths = Files.walk(REPRESENTATIVE_MODEL_DIR)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".phylospec"))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
    }

    private BeastXModel buildModelFromFile(Path path) throws Exception {
        String source =
                readSource(path);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildModel("test");
    }

    private String readSource(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }

    private boolean hasPhylogeneticContent(BeastXModelSummary summary) {
        return !summary.stateNodes.isEmpty()
                || !summary.parameterPriors.isEmpty()
                || !summary.treePriors.isEmpty()
                || !summary.likelihoods.isEmpty()
                || !summary.operators.isEmpty();
    }

    private void assertNoBlankValues(
            List<String> values,
            String label,
            Path modelPath
    ) {
        for (String value : values) {
            assertFalse(
                    value == null || value.isBlank(),
                    "Blank value found in " + label + " for model: " + modelPath
            );
        }
    }
}