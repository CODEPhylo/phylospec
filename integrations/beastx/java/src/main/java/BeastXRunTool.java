import dr.app.beast.BeastVersion;
import dr.inference.mcmc.MCMC;
import dr.math.MathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ThreadLocalRandom;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.phylospec.tiling.mcmc.RunRecord;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

/** Worker entry point. The public CLI launches this JVM in the working directory of the command. */
public final class BeastXRunTool {
    private BeastXRunTool() {}

    public static void main(String[] args) {
        BeastXToolOptions options = args.length < 3 ? null : BeastXToolOptions.parse(args, 3);
        if (options == null) {
            System.err.println(
                    "Error: expected a script, a run directory, a run name and optional --seed <n>, --overwrite and --debug.");
            System.exit(2);
        }
        try {
            execute(Path.of(args[0]), Path.of(args[1]), args[2], options.seed(), options.overwrite());
        } catch (Exception | LinkageError | ServiceConfigurationError failure) {
            System.err.println("Error: " + failure.getMessage());
            if (options.debug()) failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void execute(Path script, Path runDir, String runName, Long requestedSeed, boolean overwrite)
            throws Exception {
        String source = Files.readString(script);
        RunRecord record = createRunRecord(runDir, script, source);
        try {
            // A managed run owns its destinations. Native command-line filename overrides also
            // apply when injected through JAVA_TOOL_OPTIONS and would bypass the logger contract.
            System.clearProperty("file.name.prefix");
            System.clearProperty("mpi.rank.postfix");
            System.clearProperty("smc.particle_folder");
            System.setProperty("log.allow.overwrite", "false");
            BeastXModel model = PhyloSpecRunner.buildXmlExportModel(script, source, runName,
                    warning -> {
                        System.err.println(warning);
                        unchecked(() -> record.addWarning(warning));
                    }, file -> unchecked(() -> record.addInput(null, file)), requestedSeed, requested -> {
                        BeastXToolOptions.applySeed(requested != null ? requested
                                : ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
                        unchecked(() -> record.recordSeed(
                                "modelBuilding", requested, MathUtils.getSeed(), requested == null));
                    });
            long seed = MathUtils.getSeed();
            Path xml = runDir.resolve("analysis.xml");
            BeastXExportTool.publish(new StateXmlGenerator().toXml(model), xml, false);
            String xmlHash = configure(record, model.beastState, xml, seed, overwrite);

            // Parsing may itself initialize a tree. Seed before loading the saved XML, so that
            // replay with this XML and seed uses the same native stream from initialization onward.
            MathUtils.setSeed(seed);
            Long requested = model.beastState.randomSeed;
            record.recordSeed("mcmc", requested, MathUtils.getSeed(), requested == null);
            MCMC mcmc = new XmlRunner().parse(xml);
            long chainLength = model.beastState.getSamplerSpec().chainLength();
            if (mcmc.getChainLength() != chainLength) {
                throw new IOException("The saved XML has an unexpected chain length: " + mcmc.getChainLength());
            }
            record.markRunning();
            mcmc.run();
            checkResults(record, chainLength, mcmc.getMarkovChain().getCurrentLength(), xml, xmlHash);
            record.markSucceeded();
        } catch (Exception | LinkageError | ServiceConfigurationError failure) {
            try {
                record.markFailed(failure, null);
            } catch (IOException recordFailure) {
                failure.addSuppressed(recordFailure);
            }
            throw failure;
        }
    }

    static RunRecord createRunRecord(Path runDir, Path script, String source) throws IOException {
        Path original = script.toAbsolutePath().normalize();
        return RunRecord.create(
                runDir,
                source,
                original,
                original.getParent(),
                "beastx",
                BeastVersion.INSTANCE.getVersion(),
                BeastXState.class.getPackage().getImplementationVersion());
    }

    private interface IOAction {
        void run() throws IOException;
    }

    /** Runs a record change inside a callback that cannot throw checked exceptions. */
    private static void unchecked(IOAction action) {
        try {
            action.run();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Prepares the logger files, records the settings and the saved analysis XML, and returns the
     * hash of the XML.
     */
    static String configure(RunRecord record, BeastXState state, Path xml, long seed, boolean overwrite)
            throws IOException {
        List<RunRecord.LoggerSetting> loggers = new ArrayList<>();
        for (var spec : state.screenLoggerSpecs) {
            loggers.add(new RunRecord.LoggerSetting("screen", spec.logEvery(), null));
        }
        for (var spec : state.fileLoggerSpecs) {
            loggers.add(new RunRecord.LoggerSetting("log", spec.logEvery(), spec.fileName()));
        }
        for (var spec : state.treeLoggerSpecs) {
            loggers.add(new RunRecord.LoggerSetting("tree", spec.logEvery(), spec.fileName()));
        }

        List<String> files = loggers.stream()
                .map(RunRecord.LoggerSetting::file)
                .filter(Objects::nonNull)
                .toList();
        new OutputPaths(Path.of(""), overwrite).prepare(files, null, record.getDirectory());

        String xmlHash = RunRecord.hash(xml);
        record.update(data -> data.putObject("xml")
                .put("path", record.getDirectory().relativize(xml.toAbsolutePath().normalize()).toString())
                .put("sha256", xmlHash));
        record.configure(state.getSamplerSpec().chainLength(), seed, state.runName, loggers, null);
        return xmlHash;
    }

    /**
     * Confirms that BEAST X ran the whole chain, wrote every log and left the saved analysis XML
     * unchanged.
     */
    static void checkResults(RunRecord record, long chainLength, long completedStates, Path xml, String xmlHash)
            throws IOException {
        record.update(data -> data.put("completedChainLength", completedStates));
        if (completedStates != chainLength) {
            throw new IOException("BEAST X stopped after " + completedStates + " of " + chainLength + " steps.");
        }
        for (RunRecord.OutputFile output : record.getOutputFiles()) {
            Path log = output.path();
            if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) || Files.size(log) == 0) {
                throw new IOException("BEAST X did not produce the expected log: " + log);
            }
        }
        if (!RunRecord.hash(xml).equals(xmlHash)) {
            throw new IOException("The saved analysis XML changed during execution.");
        }
    }
}
