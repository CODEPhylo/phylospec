package runner;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.spi.ToolProvider;

/** Runs a packaged engine tool without exposing the BEAST 3 host classpath. */
final class InstalledEngineTool {
    private InstalledEngineTool() {}

    static Path runtimeLib(String engine, String command) throws IOException {
        String home = System.getProperty("phylospec.home");
        if (home == null || home.isBlank()) {
            throw new IOException("Use the installed phylospec launcher for " + engine + " " + command + ".");
        }
        Path lib = Path.of(home).resolve("engines").resolve(engine).resolve("lib");
        if (!Files.isDirectory(lib)) {
            throw new IOException("Missing " + engine + " runtime in " + lib + ". Extract the complete CLI ZIP.");
        }
        return lib.toAbsolutePath().normalize();
    }

    static int run(
            PhyloSpecCli.Command requested, PhyloSpecCli.Options options, Path source, PrintStream out, PrintStream err)
            throws IOException {
        String engine = options.engine();
        String command = requested.name().toLowerCase(java.util.Locale.ROOT);
        Path lib = runtimeLib(engine, command);
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
                String name = "phylospec-" + engine + "-" + command;
                for (ToolProvider tool : ServiceLoader.load(ToolProvider.class, loader)) {
                    if (name.equals(tool.name())) {
                        List<String> args = new ArrayList<>();
                        args.add(source.toString());
                        if (options.xmlFile() != null) {
                            args.add(options.xmlFile()
                                    .toAbsolutePath()
                                    .normalize()
                                    .toString());
                        }
                        if (options.debug()) args.add("--debug");
                        return tool.run(out, err, args.toArray(String[]::new));
                    }
                }
                throw new IOException(
                        "Missing " + engine + " " + command + " tool in " + lib + ". Extract the complete CLI ZIP.");
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }
}
