package org.phylospec.beagle;

import java.nio.file.Path;

/** A discovered native BEAGLE installation ready to configure for a new JVM. */
public record BeagleRuntimeInstallation(
        Path libraryDirectory,
        Path jniLibrary
) {
    public BeagleRuntimeInstallation {
        libraryDirectory = libraryDirectory.toAbsolutePath().normalize();
        jniLibrary = jniLibrary.toAbsolutePath().normalize();

        if (!jniLibrary.getParent().equals(libraryDirectory)) {
            throw new IllegalArgumentException(
                    "The BEAGLE JNI library must be inside its library directory."
            );
        }
    }
}
