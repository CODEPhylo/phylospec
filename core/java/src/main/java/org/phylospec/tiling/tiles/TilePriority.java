package org.phylospec.tiling.tiles;

/**
 * This enum allows tiles to have different priorities and thus weights. Because the tiling algorithms seeks
 * a min-weight tiling, this allows to break ties.
 */
public enum TilePriority {
    LOW(4),
    DEFAULT(3),
    SPECIALIZED(2),
    CUSTOM(1),

    // useful for error tiles which detect invalid patterns. they will override any other tiling
    ERROR(1000);

    private final int score;

    TilePriority(int score) {
        this.score = score;
    }

    public int getWeight() {
        return this.score;
    }
}
