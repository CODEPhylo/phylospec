package org.phylospec.tiling.mcmc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/// Adds tree loggers which write to the default tree file to a list of specs. As soon as several
/// loggers use the default, each of them writes to the file of its tree instead, so that they do
/// not clash.
///
/// @param <T> the general type of trees
public final class DefaultFileTreeLoggers<T> {

    private final List<TreeLoggerSpec<T>> specs;
    private final Function<T, String> getTreeName;
    private final UnaryOperator<String> resolveFile;
    private final List<Integer> indices = new ArrayList<>();

    /// @param specs the list the tree logger specs are added to
    /// @param getTreeName returns the name of a tree, which is used in its file name
    /// @param resolveFile turns a default file name into the file name of the spec
    public DefaultFileTreeLoggers(
            List<TreeLoggerSpec<T>> specs, Function<T, String> getTreeName, UnaryOperator<String> resolveFile) {
        this.specs = specs;
        this.getTreeName = getTreeName;
        this.resolveFile = resolveFile;
    }

    /// Adds a tree logger for the given tree. The default file name is based on the output prefix.
    public void add(String outputPrefix, int logEvery, T tree) {
        this.indices.add(this.specs.size());
        this.specs.add(
                new TreeLoggerSpec<>(logEvery, this.resolveFile.apply(OutputPaths.getTreeFile(outputPrefix)), tree));

        for (int index : this.indices) {
            TreeLoggerSpec<T> spec = this.specs.get(index);
            String fileName =
                    OutputPaths.getTreeFile(outputPrefix, this.getTreeName.apply(spec.tree()), this.indices.size());
            this.specs.set(index, new TreeLoggerSpec<>(spec.logEvery(), this.resolveFile.apply(fileName), spec.tree()));
        }
    }
}
