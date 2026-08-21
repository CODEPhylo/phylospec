package org.phylospec.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A component repository holding all component libraries of a PhyloSpec repository
 * (see <a href="https://github.com/tochsner/phylospec-repository">the central repository</a>).
 * <p>
 * All versions of all component libraries are loaded. Use {@link #getLatestLibraries()} to get
 * the newest version of every library, which is what is usually registered in a
 * {@link ComponentResolver}.
 */
public class ComponentRepository {

    private static final String COMPONENTS_DIRECTORY_NAME = "components";

    // repositories are cached per process so that repeatedly loading the same repository
    // (the language server builds a resolver per document) does not hit the network every time
    private static final Map<String, ComponentRepository> loadedRepositories = new HashMap<>();

    private final String repoUri;
    private final List<ComponentLibrary> libraries;

    ComponentRepository(String repoUri, List<ComponentLibrary> libraries) {
        this.repoUri = repoUri;
        this.libraries = List.copyOf(libraries);
    }

    /**
     * Loads all component libraries of the given repository.
     * <p>
     * The repository is cached on disk, so it is only downloaded if the remote has changed. If
     * the remote cannot be reached, the cached version is used.
     */
    public static synchronized ComponentRepository load(String repoUri) throws IOException {
        ComponentRepository loadedRepository = loadedRepositories.get(repoUri);
        if (loadedRepository != null) return loadedRepository;

        Path repositoryPath = RepositoryCache.getRepositoryPath(repoUri);
        List<ComponentLibrary> libraries = loadLibraries(repositoryPath.resolve(COMPONENTS_DIRECTORY_NAME));

        ComponentRepository repository = new ComponentRepository(repoUri, libraries);
        loadedRepositories.put(repoUri, repository);
        return repository;
    }

    /**
     * Loads every component library JSON file in the given directory.
     */
    private static List<ComponentLibrary> loadLibraries(Path componentsPath) throws IOException {
        if (!Files.isDirectory(componentsPath)) {
            throw new IOException("The repository does not contain a '" + COMPONENTS_DIRECTORY_NAME + "' directory.");
        }

        List<ComponentLibrary> libraries = new ArrayList<>();

        try (Stream<Path> files = Files.list(componentsPath)) {
            for (Path file :
                    files.filter(ComponentRepository::isJsonFile).sorted().toList()) {
                try (InputStream fileStream = Files.newInputStream(file)) {
                    libraries.add(ComponentResolver.loadLibraryFromInputStream(fileStream));
                } catch (IOException | RuntimeException e) {
                    // a single malformed library must not make the entire repository unusable
                    System.err.println(
                            "Skipping the component library '" + file.getFileName() + "': " + e.getMessage());
                }
            }
        }

        return libraries;
    }

    private static boolean isJsonFile(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json");
    }

    /**
     * Returns the newest version of every component library in the repository.
     */
    public List<ComponentLibrary> getLatestLibraries() {
        Map<String, ComponentLibrary> latestLibraries = new LinkedHashMap<>();

        for (ComponentLibrary library : libraries) {
            ComponentLibrary latestLibrary = latestLibraries.get(library.getName());
            if (latestLibrary == null || compareVersions(library.getVersion(), latestLibrary.getVersion()) > 0) {
                latestLibraries.put(library.getName(), library);
            }
        }

        return List.copyOf(latestLibraries.values());
    }

    /**
     * Returns the component library with the given name and version, if the repository contains it.
     */
    public Optional<ComponentLibrary> getLibrary(String name, String version) {
        return libraries.stream()
                .filter(library ->
                        library.getName().equals(name) && library.getVersion().equals(version))
                .findFirst();
    }

    /**
     * Returns all versions of the component library with the given name, newest first.
     */
    public List<String> getAvailableVersions(String name) {
        return libraries.stream()
                .filter(library -> library.getName().equals(name))
                .map(ComponentLibrary::getVersion)
                .sorted((version, otherVersion) -> compareVersions(otherVersion, version))
                .toList();
    }

    /**
     * Compares two semantic version numbers. Returns a positive number if the first version
     * is newer than the second one.
     */
    private static int compareVersions(String version, String otherVersion) {
        String[] parts = version.split("\\.");
        String[] otherParts = otherVersion.split("\\.");

        for (int i = 0; i < Math.max(parts.length, otherParts.length); i++) {
            int part = i < parts.length ? parseVersionPart(parts[i]) : 0;
            int otherPart = i < otherParts.length ? parseVersionPart(otherParts[i]) : 0;

            if (part != otherPart) return Integer.compare(part, otherPart);
        }

        return 0;
    }

    /**
     * Parses a single part of a version number, ignoring any pre-release suffix.
     */
    private static int parseVersionPart(String part) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length() && Character.isDigit(part.charAt(i)); i++) {
            digits.append(part.charAt(i));
        }

        if (digits.isEmpty()) return 0;
        return Integer.parseInt(digits.toString());
    }
}
