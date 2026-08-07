package org.phylospec.beagle;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Child-JVM entry point for isolated native BEAGLE verification. */
public final class BeaglePreflightMain {

    static final String BEGIN_MARKER = "PHYLOSPEC_BEAGLE_PREFLIGHT_V1_BEGIN";
    static final String END_MARKER = "PHYLOSPEC_BEAGLE_PREFLIGHT_V1_END";

    private BeaglePreflightMain() {
    }

    public static void main(String[] args) {
        String factoryClass = args.length == 0
                ? "beagle.BeagleFactory"
                : args[0];
        BeagleRuntimeVerifier.Result result =
                new BeagleRuntimeVerifier(factoryClass).verify();

        write(result, new PrintWriter(System.out, true));
        System.exit(exitCode(result.status()));
    }

    private static void write(
            BeagleRuntimeVerifier.Result result,
            PrintWriter output
    ) {
        Properties properties = new Properties();
        properties.setProperty("status", result.status().name());
        properties.setProperty("version", value(result.version()));
        properties.setProperty("problem", value(result.problem()));
        properties.setProperty("resource.count", Integer.toString(result.resources().size()));

        for (int index = 0; index < result.resources().size(); index++) {
            BeagleRuntimeVerifier.Resource resource = result.resources().get(index);
            String prefix = "resource." + index + ".";
            properties.setProperty(prefix + "number", Integer.toString(resource.number()));
            properties.setProperty(prefix + "name", resource.name());
            properties.setProperty(prefix + "description", resource.description());
            properties.setProperty(prefix + "flags", Long.toString(resource.flags()));
        }

        output.println(BEGIN_MARKER);
        try {
            properties.store(output, null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        output.println(END_MARKER);
        output.flush();
    }

    private static int exitCode(BeagleRuntimeVerifier.Status status) {
        return switch (status) {
            case AVAILABLE -> 0;
            case NO_RESOURCES -> 2;
            case API_UNAVAILABLE -> 3;
            case LOAD_FAILED -> 4;
        };
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
