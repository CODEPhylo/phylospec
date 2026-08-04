package org.phylospec.workspace;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores possible folders in which a PhyloSpec model could be executed.
 * This is useful for checks that require reading files referenced in a model.
 */
public class Workspace {

    public static Set<Path> FOLDERS = new HashSet<>();
}
