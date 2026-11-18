package runner;

import beastconfig.OutputPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Runs the isolated engine with its native loggers' working directory set to output/. */
final class InstalledBeastXRun {
    private InstalledBeastXRun() {}

    static int run(PhyloSpecCli.Options options, Path source, PrintStream out, PrintStream err) throws IOException {
        out.println("Checking syntax, static types and BEAST X mappings...");
        try (var quiet = new PrintStream(OutputStream.nullOutputStream())) {
            int check = InstalledEngineTool.run(PhyloSpecCli.Command.CHECK, options, source, quiet, err);
            if (check != 0) return check;
        }
        OutputPaths.checkRuntimeOverrides();
        Path directory = options.runDirectory(source);
        String prefix = options.outputPrefix(source);
        new OutputPaths(directory.resolve("output")).resolveUserPath(prefix);
        Path lib = InstalledEngineTool.runtimeLib("beastx", "run");
        Path output = OutputPaths.createRunDirectory(directory);
        Path console = directory.resolve("console.log");
        Path record = directory.resolve("run.json");
        out.println("Results directory: " + directory);
        out.println("Saving analysis.xml and running BEAST X. Console log: " + console);

        String executable = java.io.File.separatorChar == '\\' ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        var args = new ArrayList<>(List.of(java, "--enable-native-access=ALL-UNNAMED"));
        // The macOS launcher selects the bundled CPU library on the host JVM.
        for (String key : List.of("java.library.path", "beagle.resource.order")) {
            String value = System.getProperty(key);
            if (value != null) args.add("-D" + key + "=" + value);
        }
        args.addAll(List.of("-cp", lib.resolve("*").toString(), "BeastXRunTool",
                source.toString(), directory.toString(), prefix));
        if (options.debug()) args.add("--debug");
        ProcessBuilder builder = new ProcessBuilder(args).directory(output.toFile()).redirectErrorStream(true);
        if (!options.debug()) builder.redirectOutput(console.toFile());
        int exit = execute(builder, console, options.debug(), out);

        if (!Files.isRegularFile(record)) throw new IOException("No BEAST X run record was written. See " + console);
        out.println("Run record: " + record);
        var result = new ObjectMapper().readTree(record.toFile());
        if (exit != 0 || !"succeeded".equals(result.path("status").asText())
                || !result.path("completionConfirmed").asBoolean()) {
            err.println("BEAST X run did not complete: "
                    + result.path("error").path("message").asText("see " + console));
            return exit == 130 ? 130 : 1;
        }

        out.println("Run completed (engine: beastx; libraries: beastX).");
        out.println("Analysis XML: " + directory.resolve("analysis.xml"));
        for (var file : result.path("expectedOutputs")) {
            String label = "tree".equals(file.path("kind").asText()) ? "Trees" : "Trace/log";
            out.println(label + ": " + directory.resolve(file.path("path").asText()));
        }
        out.println("Execution and output checks succeeded; MCMC convergence has not been assessed.");
        return 0;
    }

    private static int execute(ProcessBuilder builder, Path console, boolean debug, PrintStream out) throws IOException {
        Process process = builder.start();
        Thread shutdown = new Thread(process::destroy, "phylospec-beastx-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdown);
            process.getOutputStream().close(); // Managed runs supply all inputs through the script and XML.
            if (debug) {
                try (var input = process.getInputStream(); var log = Files.newOutputStream(console)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        log.write(buffer, 0, count);
                        out.write(buffer, 0, count);
                    }
                }
            }
            return process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for BEAST X.");
        } finally {
            process.destroy();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
        }
    }
}
