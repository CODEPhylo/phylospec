package org.phylospec.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;

public class LatestLibrariesTest {

    private static ComponentLibrary library(String name, String version) {
        ComponentLibrary library = new ComponentLibrary();
        library.setName(name);
        library.setVersion(version);
        return library;
    }

    private static PhyloSpecRepository repository(ComponentLibrary... libraries) {
        return new PhyloSpecRepository(List.of(libraries), List.of());
    }

    @Test
    public void bundledLibraryIsUsedWhenTheRepositoryHasNone() {
        // e.g. every library in the repository was skipped because it could not be parsed
        ComponentLibrary bundled = library("PhyloSpec Core", "2.0.0");

        List<ComponentLibrary> latest = repository().getLatestLibraries(List.of(bundled));

        assertEquals(1, latest.size());
        assertSame(bundled, latest.getFirst());
    }

    @Test
    public void newerBundledLibraryWins() {
        ComponentLibrary published = library("PhyloSpec Core", "1.4.0");
        ComponentLibrary bundled = library("PhyloSpec Core", "2.0.0");

        List<ComponentLibrary> latest = repository(published).getLatestLibraries(List.of(bundled));

        assertEquals(1, latest.size());
        assertSame(bundled, latest.getFirst());
    }

    @Test
    public void newerRepositoryLibraryWins() {
        ComponentLibrary published = library("PhyloSpec Core", "2.1.0");
        ComponentLibrary bundled = library("PhyloSpec Core", "2.0.0");

        List<ComponentLibrary> latest = repository(published).getLatestLibraries(List.of(bundled));

        assertSame(published, latest.getFirst());
    }

    @Test
    public void repositoryCopyWinsOnEqualVersions() {
        ComponentLibrary published = library("PhyloSpec Core", "2.0.0");
        ComponentLibrary bundled = library("PhyloSpec Core", "2.0.0");

        List<ComponentLibrary> latest = repository(published).getLatestLibraries(List.of(bundled));

        assertSame(published, latest.getFirst());
    }

    @Test
    public void otherRepositoryLibrariesAreKept() {
        ComponentLibrary extra = library("Extra Components", "0.1.0");
        ComponentLibrary bundled = library("PhyloSpec Core", "2.0.0");

        List<ComponentLibrary> latest = repository(extra).getLatestLibraries(List.of(bundled));

        assertEquals(2, latest.size());
        assertTrue(latest.contains(extra));
        assertTrue(latest.contains(bundled));
    }

    @Test
    public void emptyRepositoryStillGivesAWorkingComponentResolver() throws IOException {
        // previously, an empty library list made the ComponentResolver constructor throw,
        // which crashed the language server on startup
        List<ComponentLibrary> latest = repository().getLatestLibraries(ComponentResolver.loadCoreComponentLibraries());

        ComponentResolver resolver = new ComponentResolver(latest);

        assertTrue(!resolver.resolveGenerator("Normal").isEmpty());
    }
}
