package org.phylospec.beagle;

import java.util.StringJoiner;

/** Renders actionable native BEAGLE diagnostics for users and logs. */
public final class BeagleRuntimeReport {

    private BeagleRuntimeReport() {
    }

    public static String render(BeagleRuntimeLocator.Result result) {
        StringJoiner report = new StringJoiner(System.lineSeparator());
        report.add("BEAGLE native runtime search");
        report.add("Platform: " + result.platform().displayName());

        result.selected().ifPresentOrElse(
                selected -> {
                    BeagleRuntimeInstallation installation = selected.installation();
                    report.add("Status: native JNI library located (not load-tested)");
                    report.add("Configured path: " + selected.location().directory());
                    report.add("Library directory: " + installation.libraryDirectory());
                    report.add("JNI library: " + installation.jniLibrary());
                    report.add("Source: " + selected.location().source());
                    report.add("JVM option: -Djava.library.path="
                            + installation.libraryDirectory());
                },
                () -> {
                    report.add("Status: no native JNI library found");
                    report.add("Checked:");
                    for (BeagleRuntimeLocator.Probe probe : result.probes()) {
                        report.add("  " + probe.location().directory()
                                + " [" + probe.location().source() + "]: "
                                + probe.problem());
                    }
                    report.add("Install native BEAGLE or set "
                            + BeagleRuntimeConfig.HOME_ENVIRONMENT
                            + " to its installation root or library directory.");
                }
        );

        return report.toString();
    }

    public static String renderVerification(BeagleRuntimeVerifier.Result verification) {
        StringJoiner report = new StringJoiner(System.lineSeparator());
        report.add("BEAGLE native runtime verification");
        report.add("Status: " + verification.status());

        if (verification.version() != null) {
            report.add("Version: " + verification.version());
        }
        if (verification.problem() != null) {
            report.add("Problem: " + verification.problem());
        }
        if (!verification.resources().isEmpty()) {
            report.add("Resources:");
            for (BeagleRuntimeVerifier.Resource resource : verification.resources()) {
                report.add("  " + resource.number() + ": " + resource.name()
                        + " [flags=" + resource.flags() + "]");
                if (!resource.description().isBlank()) {
                    report.add("    " + resource.description());
                }
            }
        }

        return report.toString();
    }

    public static String renderPreflight(BeaglePreflightProcess.Result result) {
        StringJoiner report = new StringJoiner(System.lineSeparator());
        report.add("BEAGLE isolated preflight");
        report.add("Status: " + result.status());
        if (result.exitCode() != null) {
            report.add("Child JVM exit code: " + result.exitCode());
        }
        if (result.problem() != null) {
            report.add("Problem: " + result.problem());
        }
        if (result.verification() != null) {
            report.add(renderVerification(result.verification()));
        }
        if (!result.standardError().isBlank()) {
            report.add("Child JVM stderr:");
            for (String line : result.standardError().lines().toList()) {
                report.add("  " + line);
            }
        }
        return report.toString();
    }
}
