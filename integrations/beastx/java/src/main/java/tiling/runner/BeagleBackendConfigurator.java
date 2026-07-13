package tiling.runner;

import beagle.BeagleFactory;
import beagle.ResourceDetails;

import java.util.List;

/**
 * Verifies that a native BEAGLE implementation is available before
 * BEAST X materializes a phylogenetic likelihood.
 *
 * <p>BEAST X 10.5.0 includes GeneralBeagleImpl, but that implementation
 * does not implement getSiteLogLikelihoods(), which is required by
 * BeagleTreeLikelihood. It therefore cannot currently be used as a
 * complete pure-Java fallback.</p>
 */
public final class BeagleBackendConfigurator {

    private static final String JAVA_ONLY_PROPERTY = "java.only";

    private static boolean configured;

    private BeagleBackendConfigurator() {
    }

    public static synchronized void requireNativeBackend() {
        if (configured) {
            return;
        }

        if (Boolean.parseBoolean(
                System.getProperty(JAVA_ONLY_PROPERTY, "false")
        )) {
            throw unsupportedJavaBackend();
        }

        System.setProperty(JAVA_ONLY_PROPERTY, "false");

        if (!nativeBeagleIsAvailable()) {
            throw new IllegalStateException(
                    "Native BEAGLE is required by the current BEAST X "
                            + "likelihood implementation, but no native "
                            + "BEAGLE resource could be loaded. Install "
                            + "BEAGLE and configure java.library.path and "
                            + "BEAGLE_PLUGIN_PATH. The bundled pure-Java "
                            + "GeneralBeagleImpl cannot be used because "
                            + "getSiteLogLikelihoods() is not implemented."
            );
        }

        configured = true;

        System.out.println(
                "Likelihood backend: native BEAGLE"
        );
    }

    private static boolean nativeBeagleIsAvailable() {
        try {
            List<ResourceDetails> resources =
                    BeagleFactory.getResourceDetails();

            return resources != null
                    && !resources.isEmpty();
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private static IllegalStateException unsupportedJavaBackend() {
        return new IllegalStateException(
                "The pure-Java BEAGLE backend requested through "
                        + "-Djava.only=true is not compatible with the "
                        + "current BEAST X BeagleTreeLikelihood. "
                        + "GeneralBeagleImpl.getSiteLogLikelihoods() "
                        + "is not implemented."
        );
    }
}