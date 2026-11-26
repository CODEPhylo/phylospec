package tiling.runner;

import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import tiling.BeastXState;

import java.nio.file.Path;

/**
 * Options applied when running or partially building a BEAST X pipeline.
 */
public record RunnerOptions(
        String runName,
        RunMode mode,
        Long chainLengthOverride,
        boolean materializePhyloCTMC,
        Path outputDirectory,
        String outputFilePrefix
) {

    public RunnerOptions {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null.");
        }

        if (chainLengthOverride != null && chainLengthOverride <= 0) {
            throw new IllegalArgumentException("chainLengthOverride must be positive.");
        }

        if (outputFilePrefix != null && outputFilePrefix.isBlank()) {
            throw new IllegalArgumentException("outputFilePrefix must not be blank.");
        }
    }

    public static RunnerOptions of(String runName) {
        return builder(runName).build();
    }

    public static Builder builder(String runName) {
        return new Builder(runName);
    }

    public Builder toBuilder() {
        return new Builder(this.runName)
                .mode(this.mode)
                .chainLengthOverride(this.chainLengthOverride)
                .materializePhyloCTMC(this.materializePhyloCTMC)
                .outputDirectory(this.outputDirectory)
                .outputFilePrefix(this.outputFilePrefix);
    }

    public void applyTo(BeastXState beastState) {
        if (chainLengthOverride != null) {
            beastState.setSamplerSpec(new McmcSamplerSpec(chainLengthOverride));
        }

        String resolvedOutputPrefix =
                resolvedOutputPrefix();

        if (resolvedOutputPrefix != null) {
            beastState.outputPrefix = resolvedOutputPrefix;
        }
    }

    public String resolvedOutputPrefix() {
        if (outputFilePrefix == null) {
            return null;
        }

        if (outputDirectory == null) {
            return outputFilePrefix;
        }

        return outputDirectory.resolve(outputFilePrefix).toString();
    }

    public static class Builder {
        private final String runName;
        private RunMode mode = RunMode.BUILD_MCMC;
        private Long chainLengthOverride;
        private boolean materializePhyloCTMC;
        private Path outputDirectory;
        private String outputFilePrefix;

        private Builder(String runName) {
            this.runName = runName;
        }

        public Builder mode(RunMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder chainLengthOverride(Long chainLengthOverride) {
            this.chainLengthOverride = chainLengthOverride;
            return this;
        }

        public Builder chainLengthOverride(long chainLengthOverride) {
            this.chainLengthOverride = chainLengthOverride;
            return this;
        }

        public Builder materializePhyloCTMC(boolean materializePhyloCTMC) {
            this.materializePhyloCTMC = materializePhyloCTMC;
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder outputFilePrefix(String outputFilePrefix) {
            this.outputFilePrefix = outputFilePrefix;
            return this;
        }

        public Builder outputPrefix(Path outputDirectory, String outputFilePrefix) {
            this.outputDirectory = outputDirectory;
            this.outputFilePrefix = outputFilePrefix;
            return this;
        }

        public RunnerOptions build() {
            return new RunnerOptions(
                    this.runName,
                    this.mode,
                    this.chainLengthOverride,
                    this.materializePhyloCTMC,
                    this.outputDirectory,
                    this.outputFilePrefix
            );
        }
    }
}
