import dr.inference.mcmc.MCMC;
import dr.math.MathUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ThreadLocalRandom;
import tiling.BeastXModel;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

/** Worker entry point. The public CLI launches this JVM in the working directory of the command. */
public final class BeastXRunTool {
    private BeastXRunTool() {}

    public static void main(String[] args) {
        BeastXToolOptions options = args.length < 3 ? null : BeastXToolOptions.parse(args, 3);
        if (options == null) {
            System.err.println(
                    "Error: expected a script, a run directory, a run name and optional --seed <n> and --debug.");
            System.exit(2);
        }
        try {
            execute(Path.of(args[0]), Path.of(args[1]), args[2], options.seed());
        } catch (Exception | LinkageError | ServiceConfigurationError failure) {
            System.err.println("Error: " + failure.getMessage());
            if (options.debug()) failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void execute(Path script, Path runDir, String runName, Long requestedSeed) throws Exception {
        BeastXRunRecord record = BeastXRunRecord.create(runDir, script);
        try {
            // A managed run owns its destinations. Native command-line filename overrides also
            // apply when injected through JAVA_TOOL_OPTIONS and would bypass the logger contract.
            System.clearProperty("file.name.prefix");
            System.clearProperty("mpi.rank.postfix");
            System.clearProperty("smc.particle_folder");
            System.setProperty("log.allow.overwrite", "false");
            BeastXModel model = PhyloSpecRunner.buildXmlExportModel(script, record.sourceText(), runName,
                    warning -> {
                        System.err.println(warning);
                        record.warning(warning);
                    }, record::input, requestedSeed, requested -> {
                        BeastXToolOptions.applySeed(requested != null ? requested
                                : ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
                        record.seed("modelBuilding", requested, MathUtils.getSeed());
                    });
            long seed = MathUtils.getSeed();
            Path xml = runDir.resolve("analysis.xml");
            BeastXExportTool.publish(new StateXmlGenerator().toXml(model), xml, false);
            record.configure(model.beastState, xml, seed);

            // Parsing may itself initialize a tree. Seed before loading the saved XML, so that
            // replay with this XML and seed uses the same native stream from initialization onward.
            MathUtils.setSeed(seed);
            record.seed("mcmc", model.beastState.randomSeed, MathUtils.getSeed());
            MCMC mcmc = new XmlRunner().parse(xml);
            if (mcmc.getChainLength() != model.beastState.getSamplerSpec().chainLength()) {
                throw new IOException("The saved XML has an unexpected chain length: " + mcmc.getChainLength());
            }
            record.running();
            mcmc.run();
            record.succeeded(mcmc.getMarkovChain().getCurrentLength());
        } catch (Exception | LinkageError | ServiceConfigurationError failure) {
            try {
                record.failed(failure);
            } catch (IOException recordFailure) {
                failure.addSuppressed(recordFailure);
            }
            throw failure;
        }
    }
}
