import beast.base.inference.MCMC;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Beast3TestGTRExampleTest {

    private static final Path SOURCE_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "resources",
                    "comparison",
                    "examples",
                    "testGTR",
                    "testGTR.phylospec"
            );

    private static final Path REFERENCE_XML_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "resources",
                    "comparison",
                    "examples",
                    "testGTR",
                    "beast2-testGTR.xml"
            );

    private static final Path OUTPUT_DIRECTORY =
            Path.of(
                    "target",
                    "comparison",
                    "examples",
                    "testGTR"
            );

    private static final String RUN_NAME =
            "phylospec-beast3-testGTR";

    @Test
    public void buildsTestGTRMcmc() throws Exception {
        assertTrue(
                Files.exists(SOURCE_PATH),
                "Expected the PhyloSpec testGTR source."
        );

        assertTrue(
                Files.exists(REFERENCE_XML_PATH),
                "Expected the BEAST 2 testGTR reference XML."
        );

        String source =
                Files.readString(SOURCE_PATH);

        MCMC mcmc =
                new PhyloSpecRunner(source)
                        .buildMCMC(
                                OUTPUT_DIRECTORY
                                        .resolve(RUN_NAME)
                                        .toString()
                        );

        assertNotNull(mcmc);
        assertNotNull(mcmc.posteriorInput.get());

        assertFalse(
                mcmc.operatorsInput.get().isEmpty(),
                "Expected the generated MCMC to contain operators."
        );

        assertTrue(
                mcmc.operatorsInput.get().stream()
                        .anyMatch(operator ->
                                operator.listStateNodes().stream()
                                        .anyMatch(stateNode ->
                                                "rateAC".equals(
                                                        stateNode.getID()
                                                )
                                        )
                        ),
                "Expected rateAC to be covered by an operator."
        );

        assertTrue(
                mcmc.operatorsInput.get().stream()
                        .anyMatch(operator ->
                                operator.listStateNodes().stream()
                                        .anyMatch(stateNode ->
                                                "baseFrequencies".equals(
                                                        stateNode.getID()
                                                )
                                        )
                        ),
                "Expected base frequencies to be covered by an operator."
        );
    }

    @Test
    public void writesTestGTRBeast3Xml() throws Exception {
        Files.createDirectories(
                OUTPUT_DIRECTORY
        );

        Path xmlPath =
                OUTPUT_DIRECTORY.resolve(
                        RUN_NAME + ".xml"
                );

        Path operatorSummaryPath =
                OUTPUT_DIRECTORY.resolve(
                        RUN_NAME + ".operators.txt"
                );

        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        String source =
                Files.readString(SOURCE_PATH);

        new PhyloSpecRunner(source)
                .writeXml(
                        OUTPUT_DIRECTORY
                                .resolve(RUN_NAME)
                                .toString(),
                        xmlPath,
                        operatorSummaryPath
                );

        assertTrue(
                Files.exists(xmlPath),
                "Expected BEAST 3 XML to be generated."
        );

        assertTrue(
                Files.size(xmlPath) > 0,
                "Expected generated XML to be non-empty."
        );

        assertTrue(
                Files.exists(operatorSummaryPath),
                "Expected operator summary to be generated."
        );

        assertTrue(
                Files.size(operatorSummaryPath) > 0,
                "Expected operator summary to be non-empty."
        );

        String xml =
                Files.readString(xmlPath);

        assertTrue(
                xml.contains("GTR"),
                "Expected generated XML to contain a GTR model."
        );

        assertTrue(
                xml.contains("rateAC"),
                "Expected generated XML to contain rateAC."
        );

        assertTrue(
                xml.contains("rateAG"),
                "Expected generated XML to contain rateAG."
        );

        assertTrue(
                xml.contains("rateAT"),
                "Expected generated XML to contain rateAT."
        );

        assertTrue(
                xml.contains("rateCG"),
                "Expected generated XML to contain rateCG."
        );

        assertTrue(
                xml.contains("rateGT"),
                "Expected generated XML to contain rateGT."
        );

        assertTrue(
                xml.contains("baseFrequencies"),
                "Expected generated XML to contain estimated frequencies."
        );

        assertTrue(
                xml.contains("codingFirstLikelihood"),
                "Expected coding-first likelihood."
        );

        assertTrue(
                xml.contains("codingSecondLikelihood"),
                "Expected coding-second likelihood."
        );

        assertTrue(
                xml.contains("noncodingFirstLikelihood"),
                "Expected noncoding-first likelihood."
        );

        assertTrue(
                xml.contains("noncodingSecondLikelihood"),
                "Expected noncoding-second likelihood."
        );

        assertTrue(
                xml.contains("noncodingThirdLikelihood"),
                "Expected noncoding-third likelihood."
        );

        String operators =
                Files.readString(operatorSummaryPath);

        assertTrue(
                operators.contains("rateAC"),
                "Expected rateAC in the operator summary."
        );

        assertTrue(
                operators.contains("rateAG"),
                "Expected rateAG in the operator summary."
        );

        assertTrue(
                operators.contains("rateAT"),
                "Expected rateAT in the operator summary."
        );

        assertTrue(
                operators.contains("rateCG"),
                "Expected rateCG in the operator summary."
        );

        assertTrue(
                operators.contains("rateGT"),
                "Expected rateGT in the operator summary."
        );

        assertTrue(
                operators.contains("baseFrequencies"),
                "Expected base frequencies in the operator summary."
        );
    }

    @Test
    public void runsShortTestGTRMcmc() throws Exception {
        Files.createDirectories(
                OUTPUT_DIRECTORY
        );

        String outputPrefix =
                OUTPUT_DIRECTORY
                        .resolve(RUN_NAME)
                        .toString();

        Path logPath =
                Path.of(outputPrefix + ".log");

        Path treePath =
                Path.of(outputPrefix + ".trees");

        Path operatorSummaryPath =
                Path.of(
                        outputPrefix
                                + ".operators.txt"
                );

        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treePath);
        Files.deleteIfExists(operatorSummaryPath);

        String source =
                Files.readString(SOURCE_PATH);

        new PhyloSpecRunner(source)
                .runPhyloSpec(
                        outputPrefix,
                        operatorSummaryPath
                );

        assertTrue(
                Files.exists(logPath),
                "Expected the short MCMC parameter log."
        );

        assertTrue(
                Files.size(logPath) > 0,
                "Expected a non-empty parameter log."
        );

        assertTrue(
                Files.exists(treePath),
                "Expected the short MCMC tree log."
        );

        assertTrue(
                Files.size(treePath) > 0,
                "Expected a non-empty tree log."
        );

        assertTrue(
                Files.exists(operatorSummaryPath),
                "Expected the operator summary."
        );

        String log =
                Files.readString(logPath);

        assertTrue(
                log.contains("posterior"),
                "Expected posterior in the parameter log."
        );

        assertTrue(
                log.contains("likelihood"),
                "Expected likelihood in the parameter log."
        );

        assertTrue(
                log.contains("rateAC"),
                "Expected rateAC in the parameter log."
        );

        assertTrue(
                log.contains("rateAG"),
                "Expected rateAG in the parameter log."
        );

        assertTrue(
                log.contains("rateAT"),
                "Expected rateAT in the parameter log."
        );

        assertTrue(
                log.contains("rateCG"),
                "Expected rateCG in the parameter log."
        );

        assertTrue(
                log.contains("rateGT"),
                "Expected rateGT in the parameter log."
        );

        assertTrue(
                log.contains("baseFrequencies"),
                "Expected base frequencies in the parameter log."
        );
    }
}
