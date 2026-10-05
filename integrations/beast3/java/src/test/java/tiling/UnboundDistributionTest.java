package tiling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import beast.base.core.Input;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beastconfig.BEASTState;
import java.util.List;
import java.util.Random;
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

    @Test
    public void bindsAChildWithoutRegisteringItAsAnIndependentLikelihood() {
        BEASTState state = new BEASTState("mixture");
        CompoundDistribution child = new CompoundDistribution();
        AtomicReference<CustomObservation> boundValue = new AtomicReference<>();
        UnboundDistribution<CustomObservation, CompoundDistribution> distribution =
                new UnboundDistribution<>(child, boundValue::set);
        CustomObservation observation = new CustomObservation("shared-data");

        distribution.bindAsComponent(observation);

        assertSame(observation, boundValue.get());
        assertEquals(0, state.likelihoodDistributions.size());
        assertThrows(RuntimeException.class, () -> distribution.bindAsComponent(observation));
        assertThrows(RuntimeException.class, () -> distribution.observeAs(state, observation, "duplicate-likelihood"));
        assertEquals(0, state.likelihoodDistributions.size());
    }

    @Test
    public void consumesAnUnboundDistributionForComposition() {
        BEASTState state = new BEASTState("composed-model");
        RequiredDistribution intermediate = new RequiredDistribution();
        intermediate.determindClassOfInputs();
        state.setInput(intermediate, intermediate.configuredInput, "configured");
        UnboundDistribution<Object, RequiredDistribution> distribution =
                new UnboundDistribution<>(intermediate, ignored -> {});

        assertSame(intermediate, distribution.consumeForComposition(state));
        assertDoesNotThrow(state::initializeBEASTObjects);
        assertThrows(RuntimeException.class, () -> distribution.observeAs(state, new Object(), "likelihood"));
    }

    private record CustomObservation(String id) {}

    public static final class RequiredDistribution extends Distribution {
        public final Input<String> requiredInput =
                new Input<>("required", "value supplied only when directly used", Input.Validate.REQUIRED);
        public final Input<String> configuredInput =
                new Input<>("configured", "already configured value", Input.Validate.OPTIONAL);

        @Override
        public List<String> getArguments() {
            return List.of();
        }

        @Override
        public List<String> getConditions() {
            return List.of();
        }

        @Override
        public void sample(State state, Random random) {}
    }
}
