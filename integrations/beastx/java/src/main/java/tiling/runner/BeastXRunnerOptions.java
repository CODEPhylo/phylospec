package tiling.runner;

public record BeastXRunnerOptions(
        String runName,
        BeastXRunMode mode,
        Long chainLengthOverride,
        boolean materializePhyloCTMC
) {

    public BeastXRunnerOptions {
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

    public static BeastXRunnerOptions of(String runName) {
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
        private BeastXRunMode mode = BeastXRunMode.BUILD_MCMC;
        private Long chainLengthOverride;
        private boolean materializePhyloCTMC;

        private Builder(String runName) {
            this.runName =
                    runName;
        }

        public Builder mode(BeastXRunMode mode) {
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

        public BeastXRunnerOptions build() {
            return new BeastXRunnerOptions(
                    this.runName,
                    this.mode,
                    this.chainLengthOverride,
                    this.materializePhyloCTMC
            );
        }
    }
}