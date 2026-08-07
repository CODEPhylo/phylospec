package tiling.runner;

import org.phylospec.beagle.BeagleRuntimeLocator;
import org.phylospec.beagle.BeagleRuntimeReport;
import org.phylospec.beagle.BeagleRuntimeVerifier;

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

        BeagleRuntimeVerifier.Result verification =
                new BeagleRuntimeVerifier().verify();

        if (!verification.available()) {
            BeagleRuntimeLocator.Result runtimeSearch =
                    new BeagleRuntimeLocator().locate();
            throw new IllegalStateException(
                    "Native BEAGLE is required by the current BEAST X "
                            + "likelihood implementation, but no native "
                            + "BEAGLE resource could be loaded. "
                            + "No acceptable BEAGLE library plugins found. Install "
                            + "BEAGLE and configure java.library.path and "
                            + "BEAGLE_PLUGIN_PATH. The bundled pure-Java "
                            + "GeneralBeagleImpl cannot be used because "
                            + "getSiteLogLikelihoods() is not implemented."
                            + System.lineSeparator()
                            + BeagleRuntimeReport.renderVerification(verification)
                            + System.lineSeparator()
                            + BeagleRuntimeReport.render(runtimeSearch)
            );
        }

        configured = true;

        System.out.println(
                "Likelihood backend: native BEAGLE " + verification.version()
        );
        System.out.println(
                "BEAGLE resources: "
                        + verification.resources().stream()
                        .map(resource -> resource.number() + ":" + resource.name())
                        .toList()
        );
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
