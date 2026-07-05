package tiling.mcmc;

import dr.evolution.tree.Tree;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.NumberColumn;

import java.util.List;
import java.util.Objects;

public final class TreeStatisticsLoggable implements Loggable {

    private final TreeModel treeModel;
    private final String treeName;
    private final List<TreeStatistic> statistics;

    private TreeStatisticsLoggable(
            TreeModel treeModel,
            String treeName,
            List<TreeStatistic> statistics
    ) {
        this.treeModel =
                Objects.requireNonNull(treeModel, "treeModel must not be null.");

        if (treeName == null || treeName.isBlank()) {
            throw new IllegalArgumentException("treeName must not be blank.");
        }

        this.treeName =
                treeName;

        this.statistics =
                List.copyOf(statistics);
    }

    public static TreeStatisticsLoggable all(
            TreeModel treeModel,
            String treeName
    ) {
        return new TreeStatisticsLoggable(
                treeModel,
                treeName,
                List.of(
                        TreeStatistic.HEIGHT,
                        TreeStatistic.TREE_LENGTH
                )
        );
    }

    public static TreeStatisticsLoggable height(
            TreeModel treeModel,
            String treeName
    ) {
        return new TreeStatisticsLoggable(
                treeModel,
                treeName,
                List.of(TreeStatistic.HEIGHT)
        );
    }

    public static TreeStatisticsLoggable treeLength(
            TreeModel treeModel,
            String treeName
    ) {
        return new TreeStatisticsLoggable(
                treeModel,
                treeName,
                List.of(TreeStatistic.TREE_LENGTH)
        );
    }

    @Override
    public LogColumn[] getColumns() {
        return this.statistics.stream()
                .map(this::column)
                .toArray(LogColumn[]::new);
    }

    private LogColumn column(TreeStatistic statistic) {
        return new NumberColumn(this.treeName + "." + statistic.label) {
            @Override
            public double getDoubleValue() {
                return switch (statistic) {
                    case HEIGHT -> treeModel.getNodeHeight(treeModel.getRoot());
                    case TREE_LENGTH -> Tree.getTreeLength(treeModel);
                };
            }
        };
    }

    private enum TreeStatistic {
        HEIGHT("height"),
        TREE_LENGTH("treeLength");

        private final String label;

        TreeStatistic(String label) {
            this.label =
                    label;
        }
    }
}
