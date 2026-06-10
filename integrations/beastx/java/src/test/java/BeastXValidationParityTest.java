import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXValidationParityTest {

    @Test
    public void relaxedClockRejectsBaseMeanOtherThanOne() {
        String source = """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)

                Vector<Rate> branchRates ~ RelaxedClock(
                    clockRate=1.0,
                    base=LogNormal(mean=2.0, logSd=0.1),
                    tree=tree
                )
                """;

        assertInvalidModel(
                source,
                "RelaxedClock base distribution must have mean 1.0",
                "mean branch rate is controlled by clockRate"
        );
    }

    @Test
    public void logNormalRejectsNonPositiveLogSd() {
        String source = """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=0.0
                )
                """;

        assertInvalidModel(
                source,
                "logSd",
                "LogNormal"
        );
    }

    @Test
    public void gammaRejectsNonPositiveShape() {
        String source = """
                PositiveReal x ~ Gamma(
                    shape=0.0,
                    rate=1.0
                )
                """;

        assertInvalidModel(
                source,
                "shape",
                "Gamma"
        );
    }

    @Test
    public void gammaRejectsNonPositiveRate() {
        String source = """
                PositiveReal x ~ Gamma(
                    shape=1.0,
                    rate=0.0
                )
                """;

        assertInvalidModel(
                source,
                "rate",
                "Gamma"
        );
    }

    @Test
    public void betaRejectsNonPositiveAlpha() {
        String source = """
                Probability x ~ Beta(
                    alpha=0.0,
                    beta=1.0
                )
                """;

        assertInvalidModel(
                source,
                "alpha",
                "Beta"
        );
    }

    @Test
    public void betaRejectsNonPositiveBeta() {
        String source = """
                Probability x ~ Beta(
                    alpha=1.0,
                    beta=0.0
                )
                """;

        assertInvalidModel(
                source,
                "beta",
                "Beta"
        );
    }

    @Test
    public void cauchyRejectsNonPositiveScale() {
        String source = """
                Real x ~ Cauchy(
                    location=0.0,
                    scale=0.0
                )
                """;

        assertInvalidModel(
                source,
                "scale",
                "Cauchy"
        );
    }

    @Test
    public void poissonRejectsNegativeRate() {
        String source = """
                Integer x ~ Poisson(
                    rate=-1.0
                )
                """;

        assertInvalidModel(
                source,
                "rate",
                "Poisson"
        );
    }

    private void assertInvalidModel(
            String source,
            String... expectedMessageParts
    ) {
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> new PhyloSpecRunner(source).buildState("validationParity")
                );

        String message =
                exception.getMessage();

        for (String expectedMessagePart : expectedMessageParts) {
            assertTrue(
                    message.contains(expectedMessagePart),
                    "Expected error message to contain: "
                            + expectedMessagePart
                            + "\n\nActual message:\n"
                            + message
            );
        }
    }
}
