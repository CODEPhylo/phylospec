package tiling.runner;

import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Captures the artifacts and output paths produced by a BEAST X run pipeline.
 */
public record BeastXRunResult(
        String runName,
        RunnerOptions options,
        BeastXState beastState,
        BeastXModel model,
        MCMC mcmc,
        boolean materialized,
        boolean executed
) {

    public BeastXRunResult {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (options == null) {
            throw new IllegalArgumentException("options must not be null.");
        }

        if (beastState == null) {
            throw new IllegalArgumentException("beastState must not be null.");
        }
    }

    public boolean hasModel() {
        return this.model != null;
    }

    public boolean hasMCMC() {
        return this.mcmc != null;
    }

    public BeastXRunResult asExecuted() {
        if (this.mcmc == null) {
            throw new IllegalStateException("Cannot mark a run as executed when no MCMC object was built.");
        }

        return new BeastXRunResult(
                this.runName,
                this.options,
                this.beastState,
                this.model,
                this.mcmc,
                this.materialized,
                true
        );
    }

    public List<Path> fileLogPaths() {
        LinkedHashSet<Path> paths =
                new LinkedHashSet<>();

        for (BeastXState.FileLoggerSpec spec : this.beastState.fileLoggerSpecs) {
            paths.add(Path.of(spec.fileName()));
        }

        if (
                this.beastState.outputPrefix != null
                        && this.beastState.fileLoggerSpecs.isEmpty()
        ) {
            paths.add(Path.of(this.beastState.outputPrefix + ".log"));
        }

        return List.copyOf(paths);
    }

    public List<Path> treeLogPaths() {
        LinkedHashSet<Path> paths =
                new LinkedHashSet<>();

        for (BeastXState.TreeLoggerSpec spec : this.beastState.treeLoggerSpecs) {
            paths.add(Path.of(spec.fileName()));
        }

        if (
                this.beastState.outputPrefix != null
                        && this.beastState.treeLoggerSpecs.isEmpty()
                        && !this.beastState.treePriorDistributions.isEmpty()
        ) {
            paths.add(Path.of(this.beastState.outputPrefix + ".trees"));
        }

        return List.copyOf(paths);
    }

    public List<Path> outputPaths() {
        ArrayList<Path> paths =
                new ArrayList<>();

        paths.addAll(fileLogPaths());
        paths.addAll(treeLogPaths());

        return List.copyOf(paths);
    }

    public Optional<Path> firstFileLogPath() {
        List<Path> paths =
                fileLogPaths();

        if (paths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(paths.getFirst());
    }

    public Optional<Path> firstTreeLogPath() {
        List<Path> paths =
                treeLogPaths();

        if (paths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(paths.getFirst());
    }

    public boolean hasFileLogs() {
        return !fileLogPaths().isEmpty();
    }

    public boolean hasTreeLogs() {
        return !treeLogPaths().isEmpty();
    }

    public boolean hasOutputFiles() {
        return !outputPaths().isEmpty();
    }
}
