package org.phylospec.typeresolver.properties;

/** Defines the canonical names and human-readable descriptions of built-in type properties. */
public final class TypePropertyNames {
    private TypePropertyNames() {}

    public static final String NUM = "num";
    public static final String NUM_COLS = "numCols";
    public static final String NUM_ROWS = "numRows";
    public static final String LITERAL = "literal";
    public static final String NUM_TAXA = "numTaxa";
    public static final String NUM_SITES = "numSites";
    public static final String NUM_BRANCHES = "numBranches";

    /**
     * Describes a property belonging to a named input.
     *
     * @param inputName the name of the input carrying the property
     * @param propertyName the property name
     * @param startOfSentence whether the description starts a sentence
     * @return a human-readable property description
     */
    public static String describeProperty(
            String inputName, String propertyName, boolean startOfSentence) {
        String article = startOfSentence ? "The" : "the";
        return switch (propertyName) {
            case LITERAL -> article + " value of '" + inputName + "'";
            case NUM -> article + " number of elements in '" + inputName + "'";
            case NUM_ROWS -> article + " number of rows in '" + inputName + "'";
            case NUM_COLS -> article + " number of columns in '" + inputName + "'";
            case NUM_TAXA -> article + " number of taxa in '" + inputName + "'";
            case NUM_SITES -> article + " number of sites in '" + inputName + "'";
            case NUM_BRANCHES -> article + " number of branches in '" + inputName + "'";
            default -> article + " '" + propertyName + "' property of '" + inputName + "'";
        };
    }
}
