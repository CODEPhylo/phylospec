package runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Command-line entry point for running a PhyloSpec source file with BEAST 3. */
public final class PhyloSpecCli {

    private static final String USAGE = """
            Usage: PhyloSpecCli [options] <model.phylospec>

            Options:
              --library <id[,id...]>  Tile libraries in preference order
              --run-name <path>       Output prefix (default: source path without .phylospec)
              -h, --help              Show this help
            """;

    private PhyloSpecCli() {}

    public static void main(String[] arguments) {
        try {
            execute(arguments);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
        } catch (ParserConfigurationException | SAXException e) {
            System.err.println("Could not run the PhyloSpec model: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Executes valid command-line arguments. */
    public static void execute(String[] arguments) throws IOException, ParserConfigurationException, SAXException {
        Options options = Options.parse(arguments);
        if (options.help()) {
            System.out.print(USAGE);
            return;
        }

        Path source = options.source().toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("PhyloSpec source file not found: " + source);
        }

        String runName = options.runName() == null
                ? withoutExtension(source).toString()
                : Path.of(options.runName()).toAbsolutePath().normalize().toString();
        new PhyloSpecRunner(source, options.libraries()).runPhyloSpec(runName);
    }

    private static Path withoutExtension(Path source) {
        String name = source.getFileName().toString();
        String suffix = ".phylospec";
        if (name.endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length());
        }
        return source.resolveSibling(name);
    }

    private record Options(Path source, String runName, List<String> libraries, boolean help) {

        private static Options parse(String[] arguments) {
            Path source = null;
            String runName = null;
            List<String> libraries = new ArrayList<>();
            boolean help = false;

            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                switch (argument) {
                    case "-h", "--help" -> help = true;
                    case "--library" -> {
                        String value = requireValue(arguments, ++index, "--library");
                        for (String id : value.split(",")) {
                            if (id.isBlank()) {
                                throw new IllegalArgumentException("Tile library identifiers must not be blank.");
                            }
                            libraries.add(id.trim());
                        }
                    }
                    case "--run-name" -> runName = requireValue(arguments, ++index, "--run-name");
                    default -> {
                        if (argument.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + argument);
                        }
                        if (source != null) {
                            throw new IllegalArgumentException("Only one PhyloSpec source file may be supplied.");
                        }
                        source = Path.of(argument);
                    }
                }
            }

            if (!help && source == null) {
                throw new IllegalArgumentException("A PhyloSpec source file is required.");
            }
            if (!help && libraries.isEmpty()) {
                throw new IllegalArgumentException("At least one --library must be supplied.");
            }
            return new Options(source, runName, List.copyOf(libraries), help);
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].startsWith("-")) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return arguments[index];
        }
    }
}
