package runner;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Makes explicitly supplied, unpacked adapter bundles visible to the existing ServiceLoader
 * pipeline for one command. This is not an installer or a native-engine hot-unloading API.
 */
final class LocalAdapterBundles implements AutoCloseable {
    private static final List<String> HOST_RUNTIME_CLASSES = List.of(
            "org/phylospec/tiling/TileLibrary.class",
            "runner/PhyloSpecRunner.class",
            "beast/base/core/BEASTObject.class",
            "beast/pkgmgmt/BEASTClassLoader.class");

    record Jar(String path, String sha256, long size, String use) {}

    record Bundle(String path, String fingerprintedAt, List<Jar> jars) {}

    private final Thread owner = Thread.currentThread();
    private final ClassLoader previous = owner.getContextClassLoader();
    private final URLClassLoader loader;
    private final List<Bundle> bundles;

    private LocalAdapterBundles(URLClassLoader loader, List<Bundle> bundles) {
        this.loader = loader;
        this.bundles = List.copyOf(bundles);
        if (loader != null) owner.setContextClassLoader(loader);
    }

    static LocalAdapterBundles open(List<Path> directories) throws IOException {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) parent = PhyloSpecCli.class.getClassLoader();
        List<URL> urls = new ArrayList<>();
        List<Bundle> bundles = new ArrayList<>();
        Set<Path> seenDirectories = new HashSet<>();
        Set<String> seenHashes = new HashSet<>();
        Map<String, Path> classes = new HashMap<>();
        Map<String, Path> metadata = new HashMap<>();
        for (Path directory : directories) {
            Path absolute = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(absolute)) {
                throw new IOException("Adapter path must be an unpacked directory containing JARs: " + absolute);
            }
            Path real = absolute.toRealPath();
            if (!seenDirectories.add(real)) throw new IOException("Adapter directory supplied more than once: " + real);
            List<Path> jars;
            try (var files = Files.list(real)) {
                jars = files.filter(file -> file.getFileName().toString().endsWith(".jar"))
                        .sorted()
                        .toList();
            }
            if (jars.isEmpty()) throw new IOException("No JARs directly inside adapter directory: " + real);
            List<Jar> inventory = new ArrayList<>();
            for (Path file : jars) {
                if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                    throw new IOException("Adapter JAR is not a readable regular file: " + file);
                }
                String hash = RunRecord.hash(file);
                String use;
                try (JarFile jar = new JarFile(file.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
                    // Existing bundles include the host runtime. Never replace or extend its classes
                    // from those copies: extensions must link against the running PhyloSpec/BEAST.
                    if (HOST_RUNTIME_CLASSES.stream().anyMatch(name -> jar.getJarEntry(name) != null)) {
                        use = "host-runtime-copy-not-added";
                    } else if (!seenHashes.add(hash)) {
                        use = "identical-jar-already-added";
                    } else {
                        var manifest = jar.getManifest();
                        String classPath = manifest == null
                                ? null
                                : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
                        if (classPath != null && !classPath.isBlank()
                                || jar.getJarEntry("META-INF/INDEX.LIST") != null) {
                            throw new IOException("Adapter JAR declares an implicit class path or JAR index: " + file
                                    + ". Put dependencies directly in the supplied bundle directories.");
                        }
                        var service = jar.getJarEntry("META-INF/services/org.phylospec.tiling.TileLibrary");
                        if (service != null) {
                            try (var input = jar.getInputStream(service)) {
                                for (String line :
                                        new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                                    String provider = line.split("#", 2)[0].trim();
                                    if (!provider.isEmpty()
                                            && parent.getResource(provider.replace('.', '/') + ".class") != null) {
                                        throw new IOException(
                                                "Local adapter provider " + provider
                                                        + " already exists in the host runtime; remove the duplicate adapter from the host class path.");
                                    }
                                }
                            }
                        }
                        // Fail on ambiguous extension classes/JSON instead of silently picking a version.
                        // Parent-provided dependencies still use Java's normal parent-first semantics.
                        for (var entry : jar.versionedStream()
                                .filter(e -> !e.isDirectory())
                                .toList()) {
                            String name = entry.getName();
                            if (name.equals("module-info.class") || name.startsWith("META-INF/versions/")) continue;
                            if (name.endsWith(".class") && parent.getResource(name) == null) {
                                requireUnique(classes, name, file);
                            } else if (name.endsWith(".json") && !name.startsWith("META-INF/maven/")) {
                                requireUnique(metadata, name, file);
                                if (parent.getResource(name) != null) {
                                    throw new IOException("Adapter JSON resource also exists in the host runtime: "
                                            + name + " in " + file + ". Use a unique resource name.");
                                }
                            }
                        }
                        urls.add(file.toUri().toURL());
                        use = "added-parent-first";
                    }
                } catch (IOException error) {
                    throw new IOException("Could not load adapter JAR " + file + ": " + error.getMessage(), error);
                }
                inventory.add(new Jar(file.toString(), hash, Files.size(file), use));
            }
            bundles.add(new Bundle(real.toString(), Instant.now().toString(), List.copyOf(inventory)));
        }
        URLClassLoader loader = directories.isEmpty() ? null : new URLClassLoader(urls.toArray(URL[]::new), parent);
        return new LocalAdapterBundles(loader, bundles);
    }

    private static void requireUnique(Map<String, Path> owners, String name, Path file) throws IOException {
        Path previous = owners.putIfAbsent(name, file);
        if (previous != null && !previous.equals(file)) {
            throw new IOException("Conflicting adapter resource '" + name + "' in " + previous + " and " + file
                    + ". Supply one version of each extension/dependency.");
        }
    }

    List<Bundle> bundles() {
        return bundles;
    }

    @Override
    public void close() throws IOException {
        if (loader != null) {
            owner.setContextClassLoader(previous);
            loader.close();
        }
    }
}
