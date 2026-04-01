package patternmatching;

public enum TilePriority {
    LOW(4),
    DEFAULT(3),
    SPECIALIZED(2),
    CUSTOM(1);

    private final int score;

    TilePriority(int score) {
        this.score = score;
    }

    public int getWeight() {
        return this.score;
    }
}
