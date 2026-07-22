package tiling.model;

/**
 * Describes how a tree model should receive its starting tree during XML export.
 */
public record StartingTreeSpec(Type type) {

    public enum Type {
        COALESCENT_SIMULATOR,
        FIXED_NEWICK
    }

    public static StartingTreeSpec coalescentSimulator() {
        return new StartingTreeSpec(Type.COALESCENT_SIMULATOR);
    }

    public static StartingTreeSpec fixedNewick() {
        return new StartingTreeSpec(Type.FIXED_NEWICK);
    }
}
