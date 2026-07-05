package tiling.model;

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
