package org.phylospec.primitives;

/**
 * String type.
 *
 * Represents a text value.
 * Used for taxon names, model identifiers, and other textual data.
 *
 * Note: This interface is named String to match PhyloSpec conventions,
 * but uses the fully qualified java.lang.String when needed to avoid conflicts.
 *
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public class Str implements Primitive<String> {
    public static final Str INSTANCE = new Str();

    protected Str() {}

    @Override
    public boolean isValid(String value) {
        return value != null;
    }
}