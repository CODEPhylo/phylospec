package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import runner.PhyloSpecException;
import runner.PhyloSpecRunner;

/**
 * An index picks one element and a range fixes how many elements a loop creates, both while the model is built.
 * A sampled value in these places would stay at its starting value during MCMC, so these models must be rejected.
 */
class SampledStructuralInputsTest {

    @Test
    void rejectsSampledIndex() {
        this.assertRejected("""
                Probability ps[i] ~ Beta(alpha=1.0, beta=1.0) for i in 1:3
                PositiveInteger z ~ Categorical(probabilities=[0.1, 0.2, 0.7])
                Boolean b ~ Bernoulli(p=ps[z])
                """, "index");
    }

    @Test
    void rejectsSampledRangeBound() {
        this.assertRejected("""
                Count n ~ Binomial(numTrials=5, p=0.5)
                Real xs[i] ~ Normal(mean=0.0, sd=1.0) for i in 1:n
                """, "to");
    }

    @Test
    void acceptsLoopVariableAsIndex() throws Exception {
        new PhyloSpecRunner("""
                Vector<Real> means = [1.0, 2.0, 3.0]
                Real x[i] ~ Normal(mean=means[i], sd=1.0) for i in 1:3
                """, List.of("beast2")).check();
    }

    private void assertRejected(String source, String expectedMessagePart) {
        PhyloSpecException exception =
                assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, List.of("beast2")).check());
        assertTrue(
                exception.getMessage().contains(expectedMessagePart),
                "Expected message to contain: " + expectedMessagePart + "\n\nActual: " + exception.getMessage());
    }
}
