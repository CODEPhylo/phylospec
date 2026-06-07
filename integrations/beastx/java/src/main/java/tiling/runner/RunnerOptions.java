package tiling.runner;

public record RunnerOptions(
        String runName,
        RunMode mode,
        Long chainLengthOverride,
        boolean materializePhyloCTMC
) {

    public RunnerOptions {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null.");
        }

        if (chainLengthOverride != null && chainLengthOverride < 0) {
            throw new IllegalArgumentException("chainLengthOverride must be non-negative.");
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
                .materializePhyloCTMC(this.materializePhyloCTMC);
    }

    public static class Builder {
        private final String runName;
        private RunMode mode = RunMode.BUILD_MCMC;
        private Long chainLengthOverride;
        private boolean materializePhyloCTMC;

        private Builder(String runName) {
            this.runName =
                    runName;
        }

        public Builder mode(RunMode mode) {
            this.mode =
                    mode;

            return this;
        }

        public Builder chainLengthOverride(Long chainLengthOverride) {
            this.chainLengthOverride =
                    chainLengthOverride;

            return this;
        }

        public Builder chainLengthOverride(long chainLengthOverride) {
            this.chainLengthOverride =
                    chainLengthOverride;

            return this;
        }

        public Builder materializePhyloCTMC(boolean materializePhyloCTMC) {
            this.materializePhyloCTMC =
                    materializePhyloCTMC;

            return this;
        }

        public RunnerOptions build() {
            return new RunnerOptions(
                    this.runName,
                    this.mode,
                    this.chainLengthOverride,
                    this.materializePhyloCTMC
            );
        }
    }
}