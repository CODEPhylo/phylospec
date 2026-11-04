package tiles.input;

/** A fixed sample description; a null species means no species was supplied. */
public record TaxonValue(String name, String species, double age) {
    public TaxonValue {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A taxon must have a nonblank name.");
        }
        if (species != null && species.isBlank()) {
            throw new IllegalArgumentException("A supplied species name must not be blank.");
        }
        if (!Double.isFinite(age) || age < 0.0) {
            throw new IllegalArgumentException("The sampling age of '" + name + "' must be finite and nonnegative.");
        }
    }
}
