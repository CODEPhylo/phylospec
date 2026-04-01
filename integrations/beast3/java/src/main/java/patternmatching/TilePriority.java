package patternmatching;

public enum TilePriority {
    DEFAULT(1),
    SPECIALIZED(2),
    CUSTOM(3);

    private final int score;

    TilePriority(int score) {
        this.score = score;
    }

    public int getWeight() {
        return this.score;
    }
}
