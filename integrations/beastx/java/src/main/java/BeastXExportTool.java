import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

/** Exports a checked native analysis through the CLI's isolated BEAST X runtime. */
public final class BeastXExportTool implements ToolProvider {
    @Override
    public String name() {
        return "phylospec-beastx-export";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !"--debug".equals(args[2]))) {
            err.println("Expected a source file, an XML output file and optional --debug.");
            return 2;
        }
        try {
            Path xmlFile = Path.of(args[1]).toAbsolutePath().normalize();
            export(Path.of(args[0]), xmlFile, err::println);
            out.println("Exported BEAST X analysis: " + xmlFile);
            return 0;
        } catch (Exception | LinkageError | ServiceConfigurationError failure) {
            err.println("Could not export " + args[0] + " with BEAST X: " + failure.getMessage());
            if (args.length == 3) failure.printStackTrace(err);
            return 1;
        }
    }

    private static void export(Path source, Path target, Consumer<String> warnings) throws Exception {
        if (target.getFileName() == null || Files.isDirectory(target)) {
            throw new IOException("Expected an XML output file: " + target);
        }
        String fileName = target.getFileName().toString();
        String prefix = fileName.endsWith(".xml") && fileName.length() > 4
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        List<Path> inputs = new ArrayList<>();
        inputs.add(source);
        BeastXModel model = PhyloSpecRunner.buildXmlExportModel(source, prefix, warnings, inputs::add);
        for (Path input : inputs) {
            if (samePath(target, input)) throw new IOException("XML output is a model input: " + input);
        }
        for (var logger : model.beastState.fileLoggerSpecs) protectLog(target, logger.fileName());
        for (var logger : model.beastState.treeLoggerSpecs) protectLog(target, logger.fileName());

        publishValidated(new StateXmlGenerator().toXml(model), target);
    }

    static void publishValidated(String xml, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), ".phylospec-export-", ".xml");
        try {
            Files.writeString(staged, xml);
            new XmlRunner().validateExport(staged);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void protectLog(Path target, String name) throws IOException {
        Path log = Path.of(name);
        if (samePath(target, log) || samePath(target, target.getParent().resolve(log))) {
            throw new IOException("XML output is also a model log: " + name);
        }
    }

    private static boolean samePath(Path first, Path second) throws IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }
}
