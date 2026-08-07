package org.phylospec.beagle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeaglePlatformTest {

    @Test
    void recognizesAppleSilicon() {
        BeaglePlatform platform = BeaglePlatform.from("Mac OS X", "aarch64");

        assertEquals(BeaglePlatform.OperatingSystem.MACOS, platform.operatingSystem());
        assertEquals(BeaglePlatform.Architecture.ARM64, platform.architecture());
        assertEquals("libhmsbeagle-jni.jnilib", platform.jniLibraryNames().getFirst());
    }

    @Test
    void recognizesLinuxAmd64() {
        BeaglePlatform platform = BeaglePlatform.from("Linux", "amd64");

        assertEquals(BeaglePlatform.OperatingSystem.LINUX, platform.operatingSystem());
        assertEquals(BeaglePlatform.Architecture.X86_64, platform.architecture());
        assertEquals("libhmsbeagle-jni.so", platform.jniLibraryNames().getFirst());
    }
}
