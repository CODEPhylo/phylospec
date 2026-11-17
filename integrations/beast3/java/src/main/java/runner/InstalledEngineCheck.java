package runner;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.spi.ToolProvider;

/** Runs an installed engine's static checker without exposing the BEAST 3 host classpath. */
final class InstalledEngineCheck {
    private InstalledEngineCheck() {}

    static int run(String engine, Path source, boolean debug, PrintStream out, PrintStream err) throws IOException {
        String home = System.getProperty("phylospec.home");
        if (home == null || home.isBlank()) {
            throw new IOException("Use the installed phylospec launcher to check with " + engine + ".");
        }
        Path lib = Path.of(home).resolve("engines").resolve(engine).resolve("lib");
        if (!Files.isDirectory(lib)) {
            throw new IOException("Missing " + engine + " runtime in " + lib + ". Extract the complete CLI ZIP.");
        }
        List<Path> jars;
        try (var files = Files.list(lib)) {
            jars = files.filter(p -> p.getFileName().toString().endsWith(".jar") && Files.isRegularFile(p))
                    .sorted()
                    .toList();
        }
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < jars.size(); i++) urls[i] = jars.get(i).toUri().toURL();

        // The integrations have classes with identical names. Only the JDK can be their common parent.
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                // Core also discovers Tile libraries and type-property providers via the context loader.
                thread.setContextClassLoader(loader);
                String name = "phylospec-" + engine + "-check";
                for (ToolProvider tool : ServiceLoader.load(ToolProvider.class, loader)) {
                    if (name.equals(tool.name())) {
                        String[] args =
                                debug ? new String[] {source.toString(), "--debug"} : new String[] {source.toString()};
                        return tool.run(out, err, args);
                    }
                }
                throw new IOException("Missing " + engine + " checker in " + lib + ". Extract the complete CLI ZIP.");
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }
}
