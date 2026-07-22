package tiling.runner;

import java.nio.file.Path;

/**
 * Options for writing, parsing, and optionally executing a BEAST X XML run.
 */
public record XmlRunnerOptions(
        String runName,
        Path xmlPath,
        boolean execute,
        boolean materializePhyloCTMC
) {

    public XmlRunnerOptions {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (xmlPath == null) {
            throw new IllegalArgumentException("xmlPath must not be null.");
        }
    }

    public static XmlRunnerOptions of(
            String runName,
            Path xmlPath
    ) {
        return builder(runName, xmlPath)
                .build();
    }

    public static Builder builder(
            String runName,
            Path xmlPath
    ) {
        return new Builder(runName, xmlPath);
    }

    public Builder toBuilder() {
        return new Builder(this.runName, this.xmlPath)
                .execute(this.execute)
                .materializePhyloCTMC(this.materializePhyloCTMC);
    }

    public static class Builder {
        private final String runName;
        private final Path xmlPath;
        private boolean execute;
        private boolean materializePhyloCTMC;

        private Builder(
                String runName,
                Path xmlPath
        ) {
            this.runName =
                    runName;

            this.xmlPath =
                    xmlPath;
        }

        public Builder execute(boolean execute) {
            this.execute =
                    execute;

            return this;
        }

        public Builder materializePhyloCTMC(boolean materializePhyloCTMC) {
            this.materializePhyloCTMC =
                    materializePhyloCTMC;

            return this;
        }

        public XmlRunnerOptions build() {
            return new XmlRunnerOptions(
                    this.runName,
                    this.xmlPath,
                    this.execute,
                    this.materializePhyloCTMC
            );
        }
    }
}
