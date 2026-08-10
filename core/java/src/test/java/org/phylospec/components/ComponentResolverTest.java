package org.phylospec.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ComponentResolverTest {
    @Test
    public void testGetUnqualifiedNameWithQualifiedNestedGenerics() {
        assertEquals(
                "Vector<Real>",
                ComponentResolver.getUnqualifiedName(
                        "phylospec.types.Vector<phylospec.types.Real>"));

        assertEquals(
                "Map<String, Vector<Real>>",
                ComponentResolver.getUnqualifiedName(
                        "phylospec.types.Map<phylospec.types.String, phylospec.types.Vector<phylospec.types.Real>>"));
    }

    @Test
    public void testPoissonGeneratesNonNegativeIntegers() throws IOException {
        ComponentResolver resolver =
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());

        Generator poisson = resolver.resolveGenerator("Poisson").getFirst();

        assertEquals(
                "phylospec.types.Distribution<phylospec.types.NonNegativeInteger>",
                poisson.getGeneratedType());
    }
}
