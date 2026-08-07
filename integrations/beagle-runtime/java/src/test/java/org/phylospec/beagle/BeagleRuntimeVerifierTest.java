package org.phylospec.beagle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeagleRuntimeVerifierTest {

    @Test
    void reportsTheVersionAndAvailableResources() {
        BeagleRuntimeVerifier.Result result = new BeagleRuntimeVerifier(
                AvailableFactory.class.getName()
        ).verify();

        assertTrue(result.available());
        assertEquals("4.0.1", result.version());
        assertEquals(1, result.resources().size());
        assertEquals(2, result.resources().getFirst().number());
        assertEquals("CPU", result.resources().getFirst().name());
        assertEquals(17L, result.resources().getFirst().flags());
    }

    @Test
    void distinguishesAnApiWithNoNativeResources() {
        BeagleRuntimeVerifier.Result result = new BeagleRuntimeVerifier(
                EmptyFactory.class.getName()
        ).verify();

        assertFalse(result.available());
        assertEquals(BeagleRuntimeVerifier.Status.NO_RESOURCES, result.status());
    }

    @Test
    void preservesTheNativeLoadFailure() {
        BeagleRuntimeVerifier.Result result = new BeagleRuntimeVerifier(
                FailingFactory.class.getName()
        ).verify();

        assertEquals(BeagleRuntimeVerifier.Status.LOAD_FAILED, result.status());
        assertTrue(result.problem().contains("wrong architecture"));
    }

    @Test
    void distinguishesAMissingJavaApi() {
        BeagleRuntimeVerifier.Result result = new BeagleRuntimeVerifier(
                "missing.beagle.BeagleFactory"
        ).verify();

        assertEquals(BeagleRuntimeVerifier.Status.API_UNAVAILABLE, result.status());
    }

    static final class AvailableFactory {
        public static String getVersion() {
            return "4.0.1";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of(new FakeResource());
        }
    }

    static final class EmptyFactory {
        public static String getVersion() {
            return "4.0.1";
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of();
        }
    }

    static final class FailingFactory {
        public static String getVersion() {
            throw new UnsatisfiedLinkError("wrong architecture");
        }

        public static List<FakeResource> getResourceDetails() {
            return List.of();
        }
    }

    static final class FakeResource {
        public int getNumber() {
            return 2;
        }

        public String getName() {
            return "CPU";
        }

        public String getDescription() {
            return "Reference CPU implementation";
        }

        public long getFlags() {
            return 17L;
        }
    }
}
