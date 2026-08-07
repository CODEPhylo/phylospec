package org.phylospec.beagle;

import java.util.List;
import java.util.Locale;

/** Operating-system information needed to locate the BEAGLE JNI library. */
public record BeaglePlatform(OperatingSystem operatingSystem, Architecture architecture) {

    public enum OperatingSystem {
        MACOS,
        LINUX,
        WINDOWS,
        OTHER
    }

    public enum Architecture {
        ARM64,
        X86_64,
        OTHER
    }

    public static BeaglePlatform current() {
        return from(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", "")
        );
    }

    public static BeaglePlatform from(String osName, String osArch) {
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        String normalizedArch = osArch.toLowerCase(Locale.ROOT);

        OperatingSystem operatingSystem;
        if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            operatingSystem = OperatingSystem.MACOS;
        } else if (normalizedOs.contains("linux")) {
            operatingSystem = OperatingSystem.LINUX;
        } else if (normalizedOs.contains("windows")) {
            operatingSystem = OperatingSystem.WINDOWS;
        } else {
            operatingSystem = OperatingSystem.OTHER;
        }

        Architecture architecture;
        if (normalizedArch.equals("aarch64") || normalizedArch.equals("arm64")) {
            architecture = Architecture.ARM64;
        } else if (normalizedArch.equals("x86_64") || normalizedArch.equals("amd64")) {
            architecture = Architecture.X86_64;
        } else {
            architecture = Architecture.OTHER;
        }

        return new BeaglePlatform(operatingSystem, architecture);
    }

    public List<String> jniLibraryNames() {
        return switch (operatingSystem) {
            case MACOS -> List.of("libhmsbeagle-jni.jnilib", "libhmsbeagle-jni.dylib");
            case LINUX -> List.of("libhmsbeagle-jni.so");
            case WINDOWS -> List.of("hmsbeagle-jni.dll", "libhmsbeagle-jni.dll");
            case OTHER -> List.of();
        };
    }

    public String displayName() {
        return operatingSystem + " " + architecture;
    }
}
