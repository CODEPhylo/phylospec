package tiling.runner;

import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;

import java.nio.file.Path;

public record BeastXXmlRunResult(
        String runName,
        BeastXModel model,
        Path xmlPath,
        MCMC mcmc,
        boolean executed
) {

    public BeastXXmlRunResult {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (model == null) {
            throw new IllegalArgumentException("model must not be null.");
        }

        if (xmlPath == null) {
            throw new IllegalArgumentException("xmlPath must not be null.");
        }

        if (mcmc == null) {
            throw new IllegalArgumentException("mcmc must not be null.");
        }
    }

    public Path outputDirectory() {
        Path parent =
                this.xmlPath.getParent();

        if (parent == null) {
            return Path.of(".");
        }

        return parent;
    }

    public BeastXXmlRunResult asExecuted() {
        return new BeastXXmlRunResult(
                this.runName,
                this.model,
                this.xmlPath,
                this.mcmc,
                true
        );
    }
}