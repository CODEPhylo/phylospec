package tiling;

public enum TilePriority {
    LOW(4),
    DEFAULT(3),
    SPECIALIZED(2),
    CUSTOM(1),
    ERROR(1000);

    private final int score;

    TilePriority(int score) {
        this.score = score;
    }

    public int getWeight() {
        return this.score;
    }
}
