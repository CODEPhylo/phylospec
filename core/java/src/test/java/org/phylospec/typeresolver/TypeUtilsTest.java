package org.phylospec.typeresolver;

import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentResolver;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeUtilsTest {
    @Test
    public void testCovers() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("PositiveReal", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Real", componentResolver),
                ResolvedType.fromString("NonNegativeReal", componentResolver),
                componentResolver
        ));

        assertTrue(TypeUtils.covers(
                ResolvedType.fromString("Vector<Real>", componentResolver),
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("PositiveReal", componentResolver),
                ResolvedType.fromString("Real", componentResolver),
                componentResolver
        ));

        assertFalse(TypeUtils.covers(
                ResolvedType.fromString("Vector<PositiveReal>", componentResolver),
                ResolvedType.fromString("Vector<Real>", componentResolver),
                componentResolver
        ));
    }

    @Test
    public void testGetLowestCover() throws IOException {
        ComponentResolver componentResolver = buildComponentResolver();

        assertEquals(
                ResolvedType.fromString("Real", componentResolver),
                TypeUtils.getLowestCover(
                    ResolvedType.fromString("Real", componentResolver),
                    ResolvedType.fromString("NonNegativeReal", componentResolver),
                    componentResolver
            )
        );
    }

    private static ComponentResolver buildComponentResolver() throws IOException {
        ComponentResolver componentResolver = new ComponentResolver();
        componentResolver.registerLibraryFromFile("../../schema/phylospec-core-component-library.json");
        componentResolver.importEntireNamespace(List.of("phylospec"));
        return componentResolver;
    }
}
