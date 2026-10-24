package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beastconfig.ReportingTreeLikelihood;

/**
 * Runs the same fixed-state and accept/reject/restore checks against bundled BEAGLE CPU.
 * Selected only by the native distribution profile; falling back to Java is a test failure.
 */
class HkyYuleBeagleIT extends HkyYuleStateTest {
    @Override
    void configureLikelihood(TreeLikelihood likelihood) {
        // Leave BEAST's native implementation selected. The independent reference in the
        // inherited tests explicitly selects Java and shares no model state or caches.
    }

    @Override
    void assertBackend(TreeLikelihood likelihood) {
        var backend = assertInstanceOf(ReportingTreeLikelihood.class, likelihood).backend();
        assertEquals("BEAGLE", backend.implementation(), "Native acceptance must not silently fall back to Java.");
        assertNull(likelihood.getLikelihoodCore());
        assertNotNull(backend.version());
        assertFalse(backend.version().isBlank());
        assertNotNull(backend.resourceNumber());
        assertNotNull(backend.resourceName());
        assertNotNull(backend.resourceFlags());
        assertTrue(backend.resourceFlags().contains("PROCESSOR_CPU"), backend.resourceFlags());
    }
}
