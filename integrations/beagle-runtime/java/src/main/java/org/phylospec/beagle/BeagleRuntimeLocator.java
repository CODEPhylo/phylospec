package org.phylospec.beagle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Finds an existing native BEAGLE installation without loading native code. */
public final class BeagleRuntimeLocator {

    public record Probe(
            BeagleRuntimeConfig.Location location,
            BeagleRuntimeInstallation installation,
            String problem
    ) {
        public boolean valid() {
            return installation != null;
        }
    }

    public record Result(BeaglePlatform platform, List<Probe> probes) {
        public Result {
            probes = List.copyOf(probes);
        }

        public Optional<Probe> selected() {
            return probes.stream().filter(Probe::valid).findFirst();
        }

        public Optional<BeagleRuntimeInstallation> installation() {
            return selected().map(Probe::installation);
        }

        public boolean found() {
            return selected().isPresent();
        }
    }

    public Result locate() {
        return locate(BeagleRuntimeConfig.fromSystem(), BeaglePlatform.current());
    }

    public Result locate(
            BeagleRuntimeConfig config,
            BeaglePlatform platform
    ) {
        List<BeagleRuntimeConfig.Location> locations = locations(config, platform);
        List<Probe> probes = locations.stream()
                .map(location -> probe(location, platform))
                .toList();
        return new Result(platform, probes);
    }

    private List<BeagleRuntimeConfig.Location> locations(
            BeagleRuntimeConfig config,
            BeaglePlatform platform
    ) {
        Map<Path, BeagleRuntimeConfig.Location> unique = new LinkedHashMap<>();

        for (BeagleRuntimeConfig.Location location : config.configuredLocations()) {
            add(unique, location.directory(), location.source());
        }

        if (config.javaLibraryPath() != null && !config.javaLibraryPath().isBlank()) {
            for (String entry : config.javaLibraryPath().split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    add(unique, Path.of(entry), BeagleRuntimeConfig.Source.JVM_LIBRARY_PATH);
                }
            }
        }

        if (config.searchCommonLocations()) {
            for (Path directory : commonLocations(platform)) {
                add(unique, directory, BeagleRuntimeConfig.Source.COMMON_SYSTEM_LOCATION);
            }
        }

        return new ArrayList<>(unique.values());
    }

    private void add(
            Map<Path, BeagleRuntimeConfig.Location> locations,
            Path directory,
            BeagleRuntimeConfig.Source source
    ) {
        Path normalized = directory.toAbsolutePath().normalize();
        locations.putIfAbsent(normalized, new BeagleRuntimeConfig.Location(normalized, source));
    }

    private List<Path> commonLocations(BeaglePlatform platform) {
        return switch (platform.operatingSystem()) {
            case MACOS -> List.of(Path.of("/opt/homebrew/lib"), Path.of("/usr/local/lib"));
            case LINUX -> List.of(
                    Path.of("/usr/local/lib"),
                    Path.of("/usr/lib"),
                    Path.of("/usr/lib/x86_64-linux-gnu"),
                    Path.of("/usr/lib/aarch64-linux-gnu")
            );
            case WINDOWS, OTHER -> List.of();
        };
    }

    private Probe probe(
            BeagleRuntimeConfig.Location location,
            BeaglePlatform platform
    ) {
        if (!Files.isDirectory(location.directory())) {
            return new Probe(location, null, "directory does not exist");
        }

        List<Path> libraryDirectories = libraryDirectories(location.directory(), platform);
        for (Path libraryDirectory : libraryDirectories) {
            for (String libraryName : platform.jniLibraryNames()) {
                Path library = libraryDirectory.resolve(libraryName);
                if (Files.isRegularFile(library)) {
                    return new Probe(
                            location,
                            new BeagleRuntimeInstallation(libraryDirectory, library),
                            null
                    );
                }
            }
        }

        String expected = platform.jniLibraryNames().isEmpty()
                ? "no JNI filename is known for this platform"
                : "expected " + String.join(" or ", platform.jniLibraryNames())
                        + " in " + libraryDirectories;
        return new Probe(location, null, expected);
    }

    private List<Path> libraryDirectories(Path configuredPath, BeaglePlatform platform) {
        List<Path> directories = new ArrayList<>();
        directories.add(configuredPath);

        if (platform.operatingSystem() == BeaglePlatform.OperatingSystem.WINDOWS) {
            directories.add(configuredPath.resolve("bin"));
        }

        directories.add(configuredPath.resolve("lib"));
        return directories.stream().distinct().toList();
    }
}
