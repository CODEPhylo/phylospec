package org.phylospec.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
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
import java.util.function.Function;
import java.util.stream.Stream;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;

/**
 * A PhyloSpec repository holding all component libraries and engine specifications of a
 * repository (see <a href="https://github.com/tochsner/phylospec-repository">the central
 * repository</a>).
 */
public class PhyloSpecRepository {

    public static final String CENTRAL_REPOSITORY_URI = "https://github.com/tochsner/phylospec-repository.git";

    private static final String COMPONENTS_DIRECTORY_NAME = "components";
    private static final String ENGINES_DIRECTORY_NAME = "engines";

    // we cache repositories so that repeatedly loading the same repository does not hit the network every time
    private static final Map<String, PhyloSpecRepository> loadedRepositories = new HashMap<>();

    private final List<ComponentLibrary> libraries;
    private final List<EngineSpecificationSchema> engines;

    private PhyloSpecRepository(List<ComponentLibrary> libraries, List<EngineSpecificationSchema> engines) {
        this.libraries = List.copyOf(libraries);
        this.engines = List.copyOf(engines);
    }

    /**
     * Loads all component libraries and engine specifications of the central PhyloSpec
     * repository, including all their versions.
     * If the repository can neither be reached nor found in the cache, an
     * {@link OfflineException} is thrown.
     */
    public static PhyloSpecRepository loadCentral() throws IOException {
        return load(CENTRAL_REPOSITORY_URI);
    }

    /**
     * Loads the core component library bundled with this package.
     * This does not need any network access, but it only contains the single core component
     * library this package was built with. No engine specifications are loaded.
     */
    public static PhyloSpecRepository loadBundled() throws IOException {
        return new PhyloSpecRepository(ComponentResolver.loadCoreComponentLibraries(), List.of());
    }

    /**
     * Loads all component libraries and engine specifications of the given repository.
     * The {@code repoUri} can be either a URL or a local path to a git repository.
     * The repository is cached on disk, so it is only downloaded if the remote has changed. If
     * the remote cannot be reached, the cached version is used.
     */
    public static synchronized PhyloSpecRepository load(String repoUri) throws IOException {
        // check if we have already loaded this repo

        PhyloSpecRepository loadedRepository = loadedRepositories.get(repoUri);
        if (loadedRepository != null) return loadedRepository;

        // obtain the path to the repo (the repo is cloned if necessary)

        Path repositoryPath = RepositoryCache.getRepositoryPath(repoUri);

        // load and parse the component libraries

        Path componentsPath = repositoryPath.resolve(COMPONENTS_DIRECTORY_NAME);
        List<ComponentLibrary> libraries =
                loadJsonFiles(componentsPath, "component library", ComponentResolver::loadLibraryFromInputStream);

        // load and parse the engine specifications

        Path enginesPath = repositoryPath.resolve(ENGINES_DIRECTORY_NAME);
        List<EngineSpecificationSchema> engines =
                loadJsonFiles(enginesPath, "engine specification", PhyloSpecRepository::loadEngineFromInputStream);

        if (libraries.isEmpty() && engines.isEmpty()) {
            throw new IOException("The repository '" + repoUri + "' contains neither a component library (in the '"
                    + COMPONENTS_DIRECTORY_NAME + "' folder) nor an engine specification (in the "
                    + ENGINES_DIRECTORY_NAME + " folder).");
        }

        // build the repo object

        PhyloSpecRepository repository = new PhyloSpecRepository(libraries, engines);
        loadedRepositories.put(repoUri, repository);
        return repository;
    }

    /**
     * Loads an engine specification from an input stream.
     */
    private static EngineSpecificationSchema loadEngineFromInputStream(InputStream fileStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(fileStream, EngineSpecificationSchema.class);
    }

    /**
     * Parses every JSON file in the given directory. Returns an empty list if the directory
     * does not exist.
     */
    private static <T> List<T> loadJsonFiles(Path directory, String description, Parser<T> parser) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();

        List<T> parsedFiles = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".json")) {
                    // this is not a JSON file
                    continue;
                }

                try (InputStream fileStream = Files.newInputStream(file)) {
                    parsedFiles.add(parser.parse(fileStream));
                } catch (IOException | RuntimeException e) {
                    System.err.println(
                            "Skipping the " + description + " '" + file.getFileName() + "': " + e.getMessage());
                }
            }
        }

        return parsedFiles;
    }

    /**
     * Parses a single JSON file of a repository.
     */
    @FunctionalInterface
    private interface Parser<T> {
        T parse(InputStream fileStream) throws IOException;
    }

    /* component libraries */

    /**
     * Returns the newest version of every component library in the repository.
     */
    public List<ComponentLibrary> getLatestLibraries() {
        return getLatest(libraries, ComponentLibrary::getName, ComponentLibrary::getVersion);
    }

    /**
     * Returns the component library with the given name and version, if the repository contains it.
     */
    public Optional<ComponentLibrary> getLibrary(String name, String version) {
        return get(libraries, ComponentLibrary::getName, ComponentLibrary::getVersion, name, version);
    }

    /**
     * Returns all versions of the component library with the given name, newest first.
     */
    public List<String> getAvailableLibraryVersions(String name) {
        return getAvailableVersions(libraries, ComponentLibrary::getName, ComponentLibrary::getVersion, name);
    }

    /* engine specifications */

    /**
     * Returns the newest version of every engine specification in the repository.
     */
    public List<EngineSpecificationSchema> getLatestEngines() {
        return getLatest(engines, EngineSpecificationSchema::getName, EngineSpecificationSchema::getEngineVersion);
    }

    /**
     * Returns the engine specification with the given name and version, if the repository
     * contains it.
     */
    public Optional<EngineSpecificationSchema> getEngine(String name, String engineVersion) {
        return get(
                engines,
                EngineSpecificationSchema::getName,
                EngineSpecificationSchema::getEngineVersion,
                name,
                engineVersion);
    }

    /**
     * Returns all versions of the engine specification with the given name, newest first.
     */
    public List<String> getAvailableEngineVersions(String name) {
        return getAvailableVersions(
                engines, EngineSpecificationSchema::getName, EngineSpecificationSchema::getEngineVersion, name);
    }

    /* helper functions to select components by name and version */

    /**
     * Returns the newest version of every component with the given name.
     */
    private static <T> List<T> getLatest(
            List<T> components, Function<T, String> getName, Function<T, String> getVersion) {
        Map<String, T> latestComponents = new LinkedHashMap<>();

        for (T component : components) {
            T latestComponent = latestComponents.get(getName.apply(component));
            if (latestComponent == null
                    || compareVersions(getVersion.apply(component), getVersion.apply(latestComponent)) > 0) {
                latestComponents.put(getName.apply(component), component);
            }
        }

        return List.copyOf(latestComponents.values());
    }

    /**
     * Returns the component with the given name and version, if there is one.
     */
    private static <T> Optional<T> get(
            List<T> components,
            Function<T, String> getName,
            Function<T, String> getVersion,
            String name,
            String version) {
        return components.stream()
                .filter(component -> getName.apply(component).equals(name)
                        && getVersion.apply(component).equals(version))
                .findFirst();
    }

    /**
     * Returns all versions of the component with the given name, newest first.
     */
    private static <T> List<String> getAvailableVersions(
            List<T> components, Function<T, String> getName, Function<T, String> getVersion, String name) {
        return components.stream()
                .filter(component -> getName.apply(component).equals(name))
                .map(getVersion)
                .sorted((version, otherVersion) -> compareVersions(otherVersion, version))
                .toList();
    }

    /* helper functions for version numbers */

    /**
     * Compares two semantic version numbers. Returns a positive number if the first version
     * is newer than the second one.
     */
    private static int compareVersions(String version, String otherVersion) {
        Optional<Version> parsedVersion = Version.tryParse(version, false);
        Optional<Version> otherParsedVersion = Version.tryParse(otherVersion, false);

        // versions which are no valid semantic versions are always considered to be older
        if (parsedVersion.isEmpty() || otherParsedVersion.isEmpty()) {
            if (parsedVersion.isPresent()) return 1;
            if (otherParsedVersion.isPresent()) return -1;
            return version.compareTo(otherVersion);
        }

        return parsedVersion.get().compareTo(otherParsedVersion.get());
    }
}
