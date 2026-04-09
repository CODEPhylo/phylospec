package tiling;

public class TilingError extends RuntimeException {
    private final String description;
    private final String hint;

    public TilingError(String description, String hint) {
        super(description + ": " + hint);
        this.description = description;
        this.hint = hint;
    }

}
