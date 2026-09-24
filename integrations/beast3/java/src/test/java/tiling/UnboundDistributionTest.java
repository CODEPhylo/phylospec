package tiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import beast.base.inference.CompoundDistribution;
import beastconfig.BEASTState;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class UnboundDistributionTest {

    @Test
    public void observesAValueThatIsNotAStateNode() {
        BEASTState state = new BEASTState("custom-observation");
        CompoundDistribution likelihood = new CompoundDistribution();
        AtomicReference<CustomObservation> boundValue = new AtomicReference<>();
        UnboundDistribution<CustomObservation, CompoundDistribution> distribution =
                new UnboundDistribution<>(likelihood, boundValue::set);
        CustomObservation observation = new CustomObservation("read-counts");

        distribution.observeAs(state, observation, "read-count-likelihood");

        assertSame(observation, boundValue.get());
        assertSame(likelihood, state.likelihoodDistributions.getFirst());
        assertEquals("read-count-likelihood", likelihood.getID());
    }

    @Test
    public void stillRejectsBindingTheSameDistributionTwice() {
        BEASTState state = new BEASTState("single-use");
        UnboundDistribution<CustomObservation, CompoundDistribution> distribution =
                new UnboundDistribution<>(new CompoundDistribution(), ignored -> {});

        distribution.observeAs(state, new CustomObservation("first"), "likelihood");

        assertThrows(
                RuntimeException.class,
                () -> distribution.observeAs(state, new CustomObservation("second"), "other-likelihood"));
    }

    private record CustomObservation(String id) {}
}
