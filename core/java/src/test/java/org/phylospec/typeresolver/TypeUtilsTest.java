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

        // integer hierarchy
        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Integer", componentResolver).iterator().next(),
                ResolvedType.fromString("NonNegativeInteger", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Integer", componentResolver).iterator().next(),
                ResolvedType.fromString("PositiveInteger", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("NonNegativeInteger", componentResolver).iterator().next(),
                ResolvedType.fromString("PositiveInteger", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("PositiveInteger", componentResolver).iterator().next(),
                ResolvedType.fromString("Integer", componentResolver).iterator().next(),
                componentResolver
        ));

        // Probability is a subtype of NonNegativeReal
        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                ResolvedType.fromString("Probability", componentResolver).iterator().next(),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("Probability", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Probability", componentResolver).iterator().next(),
                ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                componentResolver
        ));

        // Integer and Real are unrelated
        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("Integer", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Integer", componentResolver).iterator().next(),
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                componentResolver
        ));

        // Simplex extends Vector<Probability>, so Vector<Probability> covers Simplex
        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Vector<Probability>", componentResolver).iterator().next(),
                ResolvedType.fromString("Simplex", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Simplex", componentResolver).iterator().next(),
                ResolvedType.fromString("Vector<Probability>", componentResolver).iterator().next(),
                componentResolver
        ));

        // Boolean is unrelated to numeric types
        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Boolean", componentResolver).iterator().next(),
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver).iterator().next(),
                ResolvedType.fromString("Boolean", componentResolver).iterator().next(),
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

        // NonNegativeReal is the lowest cover of PositiveReal and Probability
        assertEquals(
                ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                TypeUtils.getLowestCover(
                        ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                        ResolvedType.fromString("Probability", componentResolver).iterator().next(),
                        componentResolver
                )
        );

        // NonNegativeReal is the lowest cover of PositiveReal and NonNegativeReal
        assertEquals(
                ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                TypeUtils.getLowestCover(
                        ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                        ResolvedType.fromString("NonNegativeReal", componentResolver).iterator().next(),
                        componentResolver
                )
        );

        // NonNegativeInteger is the lowest cover of PositiveInteger and NonNegativeInteger
        assertEquals(
                ResolvedType.fromString("NonNegativeInteger", componentResolver).iterator().next(),
                TypeUtils.getLowestCover(
                        ResolvedType.fromString("PositiveInteger", componentResolver).iterator().next(),
                        ResolvedType.fromString("NonNegativeInteger", componentResolver).iterator().next(),
                        componentResolver
                )
        );

        // covers with self returns self
        assertEquals(
                ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                TypeUtils.getLowestCover(
                        ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                        ResolvedType.fromString("PositiveReal", componentResolver).iterator().next(),
                        componentResolver
                )
        );
    }

    private static ComponentResolver buildComponentResolver() throws IOException {
        List<ComponentLibrary> componentLibraries = ComponentResolver.loadCoreComponentLibraries();
        return new ComponentResolver(componentLibraries);
    }
}
