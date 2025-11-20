package org.phylospec.converters;

public class RevMethodsMapping {
    /**
     * Takes a PhyloSpec type component name and a property and returns the corresponding Rev expression. Returns
     * null if the property cannot be mapped to valid Rev code.
     */
    public static StringBuilder map(
            String phylospecTypeComponentName,
            String propertyName,
            StringBuilder objectBuilder,
            RevConverter converter
    ) {
        return switch (phylospecTypeComponentName) {
            case "Tree" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".ntips()");
                case "nBranches" -> new StringBuilder("2 * ").append(objectBuilder).append(".ntips() - 2");
                case "nInternalNodes" -> new StringBuilder().append(objectBuilder).append(".ntips() - 1");
                case "taxa" -> objectBuilder.append(".taxa()");
                default -> null;
            };
            case "TaxonSet" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".size()");
                case "names" -> {
                    // we have
                    // a = taxa.names

                    // we transform this into
                    // temp_taxa := taxa
                    // for (i in 1:temp_taxa.size()) {
                    // temp_names[i] := temp_taxa[i]
                    // }
                    // a := temp_names

                    // assign temp_taxa

                    RevStmt.Assignment tempTaxaStmt = converter.addRevAssignment(
                            new RevStmt.Assignment("temp_taxa", objectBuilder)
                    );
                    String tempTaxaName = tempTaxaStmt.variableName;

                    // begin for loop

                    String indexVarName = converter.getNextAvailableVariableName("i");
                    converter.addSimpleRevStatement("for (" + indexVarName + " in 1:" + tempTaxaName + ".size()) {");

                    // assign  temp_names

                    RevStmt.Assignment tempNamesStmt = converter.addRevAssignment(
                            new RevStmt.Assignment(
                                    "temp_names",
                                    new String[] {indexVarName},
                                    new StringBuilder(tempTaxaName).append("[").append(indexVarName).append("].getName()")
                            )
                    );
                    String tempNamesName = tempNamesStmt.variableName;

                    // end for loop

                    converter.addSimpleRevStatement("}");

                    // temp_names is now in place of the original expression
                    yield new StringBuilder(tempNamesName);
                }
                default -> null;
            };
            case "Alignment" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".ntaxa()");
                case "nchar" -> objectBuilder.append(".nchar()");
                case "taxa" -> objectBuilder.append(".taxa()");
                default -> null;
            };
            default -> null;
        };
    }
}
