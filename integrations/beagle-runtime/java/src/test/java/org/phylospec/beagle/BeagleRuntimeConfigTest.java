package org.phylospec.beagle;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeagleRuntimeConfigTest {

    @Test
    void explicitPropertyTakesPrecedenceOverEnvironment() {
        Properties properties = new Properties();
        properties.setProperty(BeagleRuntimeConfig.HOME_PROPERTY, "/configured/beagle");

        BeagleRuntimeConfig config = BeagleRuntimeConfig.from(
                properties,
                Map.of(
                        BeagleRuntimeConfig.HOME_ENVIRONMENT, "/phylospec/environment",
                        BeagleRuntimeConfig.BEAGLE_LIB_ENVIRONMENT, "/beagle/environment"
                )
        );

        assertEquals(3, config.configuredLocations().size());
        assertEquals(
                Path.of("/configured/beagle"),
                config.configuredLocations().get(0).directory()
        );
        assertEquals(
                BeagleRuntimeConfig.Source.SYSTEM_PROPERTY,
                config.configuredLocations().get(0).source()
        );
        assertEquals(
                BeagleRuntimeConfig.Source.PHYLOSPEC_ENVIRONMENT,
                config.configuredLocations().get(1).source()
        );
        assertEquals(
                BeagleRuntimeConfig.Source.BEAGLE_ENVIRONMENT,
                config.configuredLocations().get(2).source()
        );
    }

    @Test
    void supportsTheLPhyBeastBeagleLibConvention() {
        BeagleRuntimeConfig config = BeagleRuntimeConfig.from(
                new Properties(),
                Map.of(BeagleRuntimeConfig.BEAGLE_LIB_ENVIRONMENT, "/lphy/beagle")
        );

        assertEquals(
                Path.of("/lphy/beagle"),
                config.configuredLocations().getFirst().directory()
        );
        assertEquals(
                BeagleRuntimeConfig.Source.BEAGLE_ENVIRONMENT,
                config.configuredLocations().getFirst().source()
        );
    }
}
