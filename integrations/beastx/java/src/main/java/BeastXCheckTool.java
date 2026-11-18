import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.spi.ToolProvider;

/** Exposes the BEAST X static check to the CLI across an isolated engine class loader. */
public final class BeastXCheckTool implements ToolProvider {
    @Override
    public String name() {
        return "phylospec-beastx-check";
    }

    /** The public CLI passes the absolute script path and, if requested, --debug. */
    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--debug".equals(args[1]))) {
            err.println("Expected a source file path and optional --debug.");
            return 2;
        }
        try {
            PhyloSpecRunner.checkFile(Path.of(args[0]), err::println);
            out.println("Check passed (engine: beastx; libraries: beastX). No MCMC was run.");
            out.println("Checked: syntax, static types and available BEAST X mappings.");
            out.println("Not checked: complete input-data validity, native model initialization or MCMC convergence.");
            return 0;
        } catch (IOException | RuntimeException | LinkageError | ServiceConfigurationError failure) {
            err.println("Could not check " + args[0] + " with BEAST X: " + failure.getMessage());
            if (args.length == 2) failure.printStackTrace(err);
            return 1;
        }
    }
}
