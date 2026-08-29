package org.phylospec.lsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.repository.PhyloSpecRepository;

/**
 * Holds the component libraries and the engines the user picked, shared by every open document.
 * The engines are maintained through the LSP commands, so this is mutated from the threads the
 * language client calls in on.
 */
class EngineRegistry {

    private final PhyloSpecRepository repository;
    private final ComponentResolver componentResolver;

    // the engines the user picked, by name, in the order they were added
    private final Map<String, EngineSpecificationSchema> selectedEngines = new LinkedHashMap<>();

    EngineRegistry() {
        PhyloSpecRepository loadedRepository = null;
        List<ComponentLibrary> libraries;

        try {
            loadedRepository = PhyloSpecRepository.loadCentral();
            libraries = loadedRepository.getLatestLibraries();
        } catch (IOException | RuntimeException e) {
            // the repository can neither be reached nor found in the cache. we fall back to the
            // bundled core library, so that a document is still lexed, parsed and type checked
            System.err.println("Could not load the PhyloSpec repository: " + e.getMessage());
            libraries = loadBundledLibraries();
        }

        this.repository = loadedRepository;
        this.componentResolver = new ComponentResolver(libraries);
    }

    /**
     * Loads the core component library this package was built with.
     */
    private static List<ComponentLibrary> loadBundledLibraries() {
        try {
            return ComponentResolver.loadCoreComponentLibraries();
        } catch (IOException e) {
            // the bundled library is on the classpath, so this should not happen
            System.err.println("Could not load the bundled component library: " + e.getMessage());
            return List.of();
        }
    }

    ComponentResolver getComponentResolver() {
        return componentResolver;
    }

    /* engines */

    /**
     * Returns the newest version of every engine the repository offers.
     */
    synchronized List<EngineSpecificationSchema> getAvailableEngines() {
        if (repository == null) return List.of();
        return repository.getLatestEngines();
    }

    /**
     * Returns the engines the user picked.
     */
    synchronized List<EngineSpecificationSchema> getSelectedEngines() {
        return List.copyOf(selectedEngines.values());
    }

    /**
     * Returns the names of the engines the user picked.
     */
    synchronized List<String> getSelectedEngineNames() {
        return List.copyOf(selectedEngines.keySet());
    }

    /**
     * Adds the engine with the given name, and with the given version if one is given. Returns
     * whether the repository holds such an engine.
     */
    synchronized boolean addEngine(String name, String version) {
        Optional<EngineSpecificationSchema> engine = version == null ? getLatestEngine(name) : getEngine(name, version);

        engine.ifPresent(specification -> selectedEngines.put(name, specification));
        return engine.isPresent();
    }

    /**
     * Removes the engine with the given name. Returns whether it was picked at all.
     */
    synchronized boolean removeEngine(String name) {
        return selectedEngines.remove(name) != null;
    }

    private Optional<EngineSpecificationSchema> getLatestEngine(String name) {
        if (repository == null) return Optional.empty();

        return repository.getLatestEngines().stream()
                .filter(engine -> engine.getName().equals(name))
                .findFirst();
    }

    private Optional<EngineSpecificationSchema> getEngine(String name, String version) {
        if (repository == null) return Optional.empty();
        return repository.getEngine(name, version);
    }

    /**
     * Returns all versions of the engine with the given name, newest first.
     */
    synchronized List<String> getAvailableEngineVersions(String name) {
        if (repository == null) return new ArrayList<>();
        return repository.getAvailableEngineVersions(name);
    }
}
