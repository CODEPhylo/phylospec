package org.phylospec.beagle;

/** Command-line entry point for checking the local native BEAGLE installation. */
public final class BeagleRuntimeDiagnostics {

    private BeagleRuntimeDiagnostics() {
    }

    public static void main(String[] args) {
        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate();
        System.out.println(BeagleRuntimeReport.render(result));
        if (!result.found()) {
            System.exit(1);
        }

        if (args.length > 0 && "--preflight".equals(args[0])) {
            BeaglePreflightProcess.Result preflight =
                    new BeaglePreflightProcess().run(
                            result.installation().orElseThrow()
                    );
            System.out.println(BeagleRuntimeReport.renderPreflight(preflight));
            if (!preflight.available()) {
                System.exit(2);
            }
        }
    }
}
