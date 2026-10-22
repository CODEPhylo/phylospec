package org.phylospec.workspace;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Stores possible folders in which a PhyloSpec model could be executed.
 * This is useful for checks that require reading files referenced in a model.
 */
public class Workspace {

    public Set<Path> FOLDERS = new HashSet<>();

    private final Path sourceDirectory;

    /** Retains the working-directory and workspace-folder search used by editor clients. */
    public Workspace() {
        sourceDirectory = null;
    }

    /** Resolves relative input paths only against this model's source directory. */
    public Workspace(Path sourceDirectory) {
        this.sourceDirectory =
                Objects.requireNonNull(sourceDirectory).toAbsolutePath().normalize();
    }

    public Optional<Path> getSourceDirectory() {
        return Optional.ofNullable(sourceDirectory);
    }
}
