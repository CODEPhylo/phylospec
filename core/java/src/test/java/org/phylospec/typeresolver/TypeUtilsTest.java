package org.phylospec.typeresolver;

import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeUtilsTest {
    @Test
    public void testCovers() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Vector<Real>", componentResolver).iterator().next(),
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver).iterator().next(),
                ResolvedType.fromString("Vector<Real>", componentResolver).iterator().next(),
                componentResolver
        ));
    }

    @Test
    public void testGetLowestCover() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertEquals(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                TypeUtils.getLowestCover(
                    ResolvedType.fromString("Real", componentResolver).iterator().next(),
                    ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                    componentResolver
            )
        );
    }

    private static ComponentResolver buildComponentResolver() throws IOException {
        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        return new ComponentResolver(componentLibraries);
    }
}
