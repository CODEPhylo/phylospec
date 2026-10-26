package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAdapterBundlesTest {

    @Test
    void scopesResourcesWithParentPrecedenceAndClosesTheLoader(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Path host = jar(directory.resolve("host.jar"), Map.of("shared.txt", "host"));
        Path bundle = Files.createDirectory(directory.resolve("bundle"));
        jar(bundle.resolve("extension.jar"), Map.of("shared.txt", "extension", "only-extension.txt", "loaded"));

        try (URLClassLoader parent = new URLClassLoader(new URL[] {host.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(parent);
            URLClassLoader scoped;
            try (LocalAdapterBundles adapters = LocalAdapterBundles.open(List.of(bundle))) {
                scoped = (URLClassLoader) Thread.currentThread().getContextClassLoader();
                assertSame(parent, scoped.getParent());
                assertEquals("host", read(scoped.getResource("shared.txt")));
                assertEquals("loaded", read(scoped.getResource("only-extension.txt")));
                assertNull(parent.getResource("only-extension.txt"));
            }
            assertSame(parent, Thread.currentThread().getContextClassLoader());
            assertNull(scoped.findResource("only-extension.txt"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void restoresContextAfterFailureInsideTheScope(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        jar(directory.resolve("extension.jar"), Map.of("only-extension.txt", "loaded"));
        IOException failure = assertThrows(IOException.class, () -> {
            try (LocalAdapterBundles adapters = LocalAdapterBundles.open(List.of(directory))) {
                throw new IOException("analysis failed");
            }
        });
        assertEquals("analysis failed", failure.getMessage());
        assertSame(original, Thread.currentThread().getContextClassLoader());
        assertNull(original.getResource("only-extension.txt"));
    }

    @Test
    void noPathsLeavesEvenANullContextLoaderUntouched() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            try (LocalAdapterBundles adapters = LocalAdapterBundles.open(List.of())) {
                assertTrue(adapters.bundles().isEmpty());
                assertNull(Thread.currentThread().getContextClassLoader());
            }
            assertNull(Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void rejectsInvalidDirectoriesAndMalformedJarsBeforeChangingContext(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Path empty = Files.createDirectory(directory.resolve("empty"));
        Path nested = Files.createDirectory(empty.resolve("nested"));
        jar(nested.resolve("not-recursively-loaded.jar"), Map.of("nested.txt", "ignored"));
        Path file = jar(directory.resolve("standalone.jar"), Map.of("standalone.txt", "file"));
        Path malformed = Files.createDirectory(directory.resolve("malformed"));
        Files.writeString(malformed.resolve("broken.jar"), "not a JAR");
        Path jarDirectory = Files.createDirectory(directory.resolve("jar-directory"));
        Files.createDirectory(jarDirectory.resolve("not-a-file.jar"));

        for (Path path : List.of(directory.resolve("missing"), empty, file, malformed, jarDirectory)) {
            IOException failure = assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(path)));
            assertTrue(failure.getMessage().contains(path.getFileName().toString()), failure.getMessage());
            assertSame(original, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    void rejectsRepeatedDirectoriesIncludingSymbolicAliases(@TempDir Path directory) throws Exception {
        Path bundle = Files.createDirectory(directory.resolve("bundle"));
        jar(bundle.resolve("extension.jar"), Map.of("extension.txt", "loaded"));
        Path alias = Files.createSymbolicLink(directory.resolve("alias"), bundle);
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        for (Path repeated : List.of(bundle, bundle.resolve("."), alias)) {
            IOException failure =
                    assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(bundle, repeated)));
            assertTrue(failure.getMessage().contains("more than once"));
            assertSame(original, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    void preservesBundleOrderSortsJarsAndRecordsDeduplication(@TempDir Path directory) throws Exception {
        Path first = Files.createDirectory(directory.resolve("z-first"));
        Path second = Files.createDirectory(directory.resolve("a-second"));
        Path added = jar(first.resolve("z-extension.jar"), Map.of("extension.txt", "loaded"));
        Path hostCopy = jar(
                first.resolve("a-host.jar"),
                Map.of("org/phylospec/tiling/TileLibrary.class", "fake host anchor", "only-host-copy.txt", "ignored"));
        Path identical = Files.copy(added, second.resolve("duplicate.jar")).toRealPath();

        try (LocalAdapterBundles adapters = LocalAdapterBundles.open(List.of(first, second))) {
            List<LocalAdapterBundles.Bundle> bundles = adapters.bundles();
            assertEquals(
                    List.of(first.toRealPath().toString(), second.toRealPath().toString()),
                    bundles.stream().map(LocalAdapterBundles.Bundle::path).toList());
            assertEquals(
                    List.of(hostCopy.toString(), added.toString()),
                    bundles.getFirst().jars().stream()
                            .map(LocalAdapterBundles.Jar::path)
                            .toList());
            assertEquals(
                    "host-runtime-copy-not-added",
                    bundles.getFirst().jars().getFirst().use());
            LocalAdapterBundles.Jar active = bundles.getFirst().jars().getLast();
            LocalAdapterBundles.Jar duplicate = bundles.getLast().jars().getFirst();
            assertEquals("added-parent-first", active.use());
            assertEquals("identical-jar-already-added", duplicate.use());
            assertEquals(identical.toString(), duplicate.path());
            assertEquals(RunRecord.hash(added), active.sha256());
            assertEquals(active.sha256(), duplicate.sha256());
            assertEquals(Files.size(added), active.size());
            for (var bundle : bundles) assertNotNull(Instant.parse(bundle.fingerprintedAt()));
            URLClassLoader loader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
            assertEquals(List.of(added.toUri().toURL()), List.of(loader.getURLs()));
            assertNull(loader.getResource("only-host-copy.txt"));
            assertEquals("loaded", read(loader.getResource("extension.txt")));
            assertThrows(UnsupportedOperationException.class, () -> bundles.clear());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> bundles.getFirst().jars().clear());
        }
    }

    @Test
    void rejectsConflictingExtensionClassesAndJsonAcrossBundles(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        int index = 0;
        for (String resource : List.of(
                "example/Extension.class", "extension-components.json", "META-INF/phylospec/engines/extension.json")) {
            Path first = Files.createDirectory(directory.resolve("first-" + index));
            Path second = Files.createDirectory(directory.resolve("second-" + index++));
            Path one = jar(first.resolve("first.jar"), Map.of(resource, "version-one"));
            Path two = jar(second.resolve("second.jar"), Map.of(resource, "version-two"));
            IOException failure =
                    assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(first, second)));
            assertTrue(failure.getMessage().contains(resource), failure.getMessage());
            assertTrue(failure.getMessage().contains(one.toString()), failure.getMessage());
            assertTrue(failure.getMessage().contains(two.toString()), failure.getMessage());
            assertSame(original, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    void rejectsJsonThatWouldResolveFromTheParent(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Path host = jar(directory.resolve("host.jar"), Map.of("extension-components.json", "old metadata"));
        Path bundle = Files.createDirectory(directory.resolve("bundle"));
        jar(bundle.resolve("extension.jar"), Map.of("extension-components.json", "new metadata"));
        try (URLClassLoader parent = new URLClassLoader(new URL[] {host.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(parent);
            IOException failure = assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(bundle)));
            assertTrue(failure.getMessage().contains("also exists in the host runtime"));
            assertTrue(failure.getMessage().contains("extension-components.json"));
            assertSame(parent, Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void rejectsALocalProviderThatWouldBeShadowedByTheHost(@TempDir Path directory) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Path host = jar(directory.resolve("host.jar"), Map.of("example/ExtensionLibrary.class", "host version"));
        Path bundle = Files.createDirectory(directory.resolve("bundle"));
        Path extension = jar(
                bundle.resolve("extension.jar"),
                Map.of(
                        "example/ExtensionLibrary.class",
                        "local version",
                        "META-INF/services/org.phylospec.tiling.TileLibrary",
                        "# extension libraries\n\n  example.ExtensionLibrary  # selected provider\n"));
        try (URLClassLoader parent = new URLClassLoader(new URL[] {host.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(parent);
            IOException failure = assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(bundle)));
            assertTrue(failure.getMessage().contains("example.ExtensionLibrary"));
            assertTrue(failure.getMessage().contains("already exists in the host runtime"));
            assertTrue(failure.getMessage().contains(extension.toString()));
            assertSame(parent, Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void rejectsImplicitClassPathsAndJarIndexes(@TempDir Path directory) throws Exception {
        Path classPath = Files.createDirectory(directory.resolve("class-path"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "../hidden.jar");
        jar(classPath.resolve("extension.jar"), Map.of("extension.txt", "loaded"), manifest);
        Path indexed = Files.createDirectory(directory.resolve("indexed"));
        jar(indexed.resolve("extension.jar"), Map.of("META-INF/INDEX.LIST", "JarIndex-Version: 1.0"));
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        for (Path path : List.of(classPath, indexed)) {
            IOException failure = assertThrows(IOException.class, () -> LocalAdapterBundles.open(List.of(path)));
            assertTrue(failure.getMessage().contains("implicit class path or JAR index"));
            assertSame(original, Thread.currentThread().getContextClassLoader());
        }
    }

    private static Path jar(Path path, Map<String, String> entries) throws IOException {
        return jar(path, entries, null);
    }

    private static Path jar(Path path, Map<String, String> entries, Manifest manifest) throws IOException {
        try (var output = Files.newOutputStream(path);
                JarOutputStream jar =
                        manifest == null ? new JarOutputStream(output) : new JarOutputStream(output, manifest)) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path.toRealPath();
    }

    private static String read(URL resource) throws IOException {
        assertNotNull(resource);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
