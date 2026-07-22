package tiling.runner;

import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.mcmc.MCMCBuilder;
import tiling.xml.StateXmlGenerator;
import tiling.xml.XmlRunner;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates BEAST X state, model, MCMC, XML, and execution steps.
 */
public final class BeastXRunPipeline {

    public BeastXRunResult run(
            BeastXState beastState,
            RunnerOptions options
    ) {
        // verify inputs

        if (beastState == null) {
            throw new IllegalArgumentException("beastState must not be null.");
        }

        if (options == null) {
            throw new IllegalArgumentException("options must not be null.");
        }

        // apply options

        options.applyTo(beastState);

        if (options.mode() == RunMode.BUILD_STATE) {
            // we have already built the state and are done
            return new BeastXRunResult(
                    options.runName(),
                    options,
                    beastState,
                    null,
                    null,
                    options.materializePhyloCTMC(),
                    false
            );
        }

        // build the BeastXModel from the BeastXState

        BeastXModel model =
                buildModel(
                        beastState,
                        options.materializePhyloCTMC()
                );

        if (options.mode() == RunMode.BUILD_MODEL) {
            // we have built the model and are done
            return new BeastXRunResult(
                    options.runName(),
                    options,
                    beastState,
                    model,
                    null,
                    options.materializePhyloCTMC(),
                    false
            );
        }

        // build the BEAST X MCMC objects

        MCMC mcmc =
                buildMCMC(model, options);

        BeastXRunResult run =
                new BeastXRunResult(
                        options.runName(),
                        options,
                        beastState,
                        model,
                        mcmc,
                        options.materializePhyloCTMC(),
                        false
                );

        if (options.mode() == RunMode.BUILD_MCMC) {
            // we have built the MCMC objects and are done
            return run;
        }

        // run MCMC

        mcmc.run();

        if (options.mode() == RunMode.EXECUTE_MCMC) {
            return run.asExecuted();
        }

        // we don't know this mode

        throw new IllegalStateException(
                "Unsupported BEAST X run mode: " + options.mode()
        );
    }

    public BeastXModel buildModel(
            BeastXState beastState,
            boolean materializePhyloCTMC
    ) {
        return BeastXModel.fromBeastXState(
                beastState,
                materializePhyloCTMC
        );
    }

    public MCMC buildMCMC(BeastXModel model) {
        return new MCMCBuilder()
                .build(model);
    }

    public MCMC buildMCMC(
            BeastXModel model,
            long chainLength
    ) {
        return new MCMCBuilder(chainLength)
                .build(model);
    }

    public MCMC buildMCMC(
            BeastXModel model,
            RunnerOptions options
    ) {
        if (options.chainLengthOverride() == null) {
            return buildMCMC(model);
        }

        return buildMCMC(
                model,
                options.chainLengthOverride()
        );
    }

    public String toXml(BeastXModel model) {
        return new StateXmlGenerator()
                .toXml(model);
    }

    public void writeXml(
            BeastXModel model,
            Path xmlPath
    ) throws IOException {
        new StateXmlGenerator()
                .write(model, xmlPath);
    }

    public MCMC parseXmlMCMC(Path xmlPath) throws Exception {
        return new XmlRunner()
                .parse(xmlPath);
    }

    public MCMC runXmlMCMC(Path xmlPath) throws Exception {
        return new XmlRunner()
                .run(xmlPath);
    }

    public XmlRunResult runXml(
            BeastXModel model,
            XmlRunnerOptions options
    ) throws Exception {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null.");
        }

        if (options == null) {
            throw new IllegalArgumentException("options must not be null.");
        }

        writeXml(model, options.xmlPath());

        MCMC mcmc =
                parseXmlMCMC(options.xmlPath());

        XmlRunResult run =
                new XmlRunResult(
                        options.runName(),
                        model,
                        options.xmlPath(),
                        mcmc,
                        false
                );

        if (!options.execute()) {
            return run;
        }

        mcmc.run();

        return run.asExecuted();
    }
}
