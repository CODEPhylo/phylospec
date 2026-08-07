package org.phylospec.beagle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** User and process settings used when locating native BEAGLE. */
public record BeagleRuntimeConfig(
        List<Location> configuredLocations,
        String javaLibraryPath,
        boolean searchCommonLocations
) {
    public enum Source {
        SYSTEM_PROPERTY,
        PHYLOSPEC_ENVIRONMENT,
        BEAGLE_ENVIRONMENT,
        JVM_LIBRARY_PATH,
        COMMON_SYSTEM_LOCATION
    }

    public record Location(Path directory, Source source) {
        public Location {
            directory = directory.toAbsolutePath().normalize();
        }
    }

    public static final String HOME_PROPERTY = "phylospec.beagle.home";
    public static final String HOME_ENVIRONMENT = "PHYLOSPEC_BEAGLE_HOME";
    public static final String BEAGLE_LIB_ENVIRONMENT = "BEAGLE_LIB";

    public BeagleRuntimeConfig {
        configuredLocations = List.copyOf(configuredLocations);
    }

    public static BeagleRuntimeConfig fromSystem() {
        return from(System.getProperties(), System.getenv());
    }

    static BeagleRuntimeConfig from(Properties properties, Map<String, String> environment) {
        List<Location> configuredLocations = new ArrayList<>();
        addConfiguredLocation(
                configuredLocations,
                properties.getProperty(HOME_PROPERTY),
                Source.SYSTEM_PROPERTY
        );
        addConfiguredLocation(
                configuredLocations,
                environment.get(HOME_ENVIRONMENT),
                Source.PHYLOSPEC_ENVIRONMENT
        );
        addConfiguredLocation(
                configuredLocations,
                environment.get(BEAGLE_LIB_ENVIRONMENT),
                Source.BEAGLE_ENVIRONMENT
        );

        return new BeagleRuntimeConfig(
                configuredLocations,
                properties.getProperty("java.library.path", ""),
                true
        );
    }

    public static BeagleRuntimeConfig explicit(Path directory) {
        return new BeagleRuntimeConfig(
                List.of(new Location(
                        directory,
                        Source.SYSTEM_PROPERTY
                )),
                "",
                false
        );
    }

    private static void addConfiguredLocation(
            List<Location> locations,
            String value,
            Source source
    ) {
        String configured = nonBlank(value);
        if (configured != null) {
            locations.add(new Location(Path.of(configured), source));
        }
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
