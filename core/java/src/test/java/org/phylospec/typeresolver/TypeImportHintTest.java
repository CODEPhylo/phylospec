package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.Type;

class TypeImportHintTest {
    @Test
    void suggestsTheExactNamespaceForANestedUnimportedType() throws IOException {
        var resolver = resolverWithSampleCounts("sample.types");
        resolver.importNamespace(List.of("sample"));

        var error = assertThrows(TypeError.class, () -> ResolvedType.fromString("Vector<SampleCount>", resolver));
        assertEquals("Add 'use sample.types' before this declaration.", error.toError(null).hint());
        resolver.importNamespace(List.of("sample", "types"));
        assertDoesNotThrow(() -> ResolvedType.fromString("Vector<SampleCount>", resolver));
    }

    @Test
    void listsAllMatchingNamespacesInOrder() throws IOException {
        var resolver = resolverWithSampleCounts("zeta", "alpha");
        var error = assertThrows(TypeError.class, () -> ResolvedType.fromString("SampleCount", List.of(), resolver, false));
        assertEquals(
                "Choose the intended namespace and add one of these imports: 'use alpha', 'use zeta'.",
                error.toError(null).hint());
    }

    @Test
    void keepsSpellingHintsAndDoesNotSuggestImportsForQualifiedNames() throws IOException {
        var resolver = resolverWithSampleCounts("sample.types");
        var typo = assertThrows(TypeError.class, () -> ResolvedType.fromString("Intege", resolver));
        assertEquals("Are you looking for 'Integer'?", typo.toError(null).hint());

        var qualified = assertThrows(TypeError.class, () -> ResolvedType.fromString("unknown.SampleCount", resolver));
        assertFalse(qualified.toError(null).hint().contains("use "));
    }

    private static ComponentResolver resolverWithSampleCounts(String... namespaces) throws IOException {
        var libraries = ComponentResolver.loadCoreComponentLibraries();
        var library = new ComponentLibrary();
        for (String namespace : namespaces) {
            var type = new Type();
            type.setName("SampleCount");
            type.setNamespace(namespace);
            library.getTypes().add(type);
        }
        libraries.add(library);
        return new ComponentResolver(libraries);
    }
}
