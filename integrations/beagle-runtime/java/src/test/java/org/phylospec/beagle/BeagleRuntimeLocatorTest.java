package org.phylospec.beagle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeagleRuntimeLocatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void findsAnExplicitNativeLibraryDirectory() throws Exception {
        Path library = temporaryDirectory.resolve("libhmsbeagle-jni.jnilib");
        Files.createFile(library);

        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate(
                BeagleRuntimeConfig.explicit(temporaryDirectory),
                BeaglePlatform.from("Mac OS X", "arm64")
        );

        assertTrue(result.found());
        assertEquals(
                library,
                result.selected().orElseThrow().installation().jniLibrary()
        );
    }

    @Test
    void acceptsAnInstallationRootContainingALibDirectory() throws Exception {
        Path libraryDirectory = Files.createDirectory(temporaryDirectory.resolve("lib"));
        Path library = Files.createFile(
                libraryDirectory.resolve("libhmsbeagle-jni.so")
        );

        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate(
                BeagleRuntimeConfig.explicit(temporaryDirectory),
                BeaglePlatform.from("Linux", "amd64")
        );

        BeagleRuntimeInstallation installation =
                result.selected().orElseThrow().installation();
        assertEquals(libraryDirectory, installation.libraryDirectory());
        assertEquals(library, installation.jniLibrary());
    }

    @Test
    void acceptsAWindowsInstallationRootContainingABinDirectory() throws Exception {
        Path binaryDirectory = Files.createDirectory(temporaryDirectory.resolve("bin"));
        Path library = Files.createFile(binaryDirectory.resolve("hmsbeagle-jni.dll"));

        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate(
                BeagleRuntimeConfig.explicit(temporaryDirectory),
                BeaglePlatform.from("Windows 11", "amd64")
        );

        BeagleRuntimeInstallation installation =
                result.selected().orElseThrow().installation();
        assertEquals(binaryDirectory, installation.libraryDirectory());
        assertEquals(library, installation.jniLibrary());
    }

    @Test
    void fallsBackThroughConfiguredCandidatesInPriorityOrder() throws Exception {
        Path missingPropertyLocation = temporaryDirectory.resolve("property");
        Path environmentLocation = Files.createDirectory(
                temporaryDirectory.resolve("environment")
        );
        Path library = Files.createFile(
                environmentLocation.resolve("libhmsbeagle-jni.so")
        );
        BeagleRuntimeConfig config = new BeagleRuntimeConfig(
                List.of(
                        new BeagleRuntimeConfig.Location(
                                missingPropertyLocation,
                                BeagleRuntimeConfig.Source.SYSTEM_PROPERTY
                        ),
                        new BeagleRuntimeConfig.Location(
                                environmentLocation,
                                BeagleRuntimeConfig.Source.PHYLOSPEC_ENVIRONMENT
                        )
                ),
                "",
                false
        );

        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate(
                config,
                BeaglePlatform.from("Linux", "amd64")
        );

        BeagleRuntimeLocator.Probe selected = result.selected().orElseThrow();
        assertEquals(
                BeagleRuntimeConfig.Source.PHYLOSPEC_ENVIRONMENT,
                selected.location().source()
        );
        assertEquals(library, selected.installation().jniLibrary());
    }

    @Test
    void explainsWhenTheJniLibraryIsMissing() {
        BeagleRuntimeLocator.Result result = new BeagleRuntimeLocator().locate(
                BeagleRuntimeConfig.explicit(temporaryDirectory),
                BeaglePlatform.from("Linux", "amd64")
        );

        assertFalse(result.found());
        String report = BeagleRuntimeReport.render(result);
        assertTrue(report.contains("no native JNI library found"));
        assertTrue(report.contains("libhmsbeagle-jni.so"));
        assertTrue(report.contains(BeagleRuntimeConfig.HOME_ENVIRONMENT));
    }
}
