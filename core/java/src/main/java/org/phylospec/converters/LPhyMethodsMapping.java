package org.phylospec.converters;

public class LPhyMethodsMapping {
    /**
     * Takes a PhyloSpec type component name and a property and returns the corresponding LPhy expression. Returns
     * null if the property cannot be mapped to valid LPhy code.
     */
    public static StringBuilder map(String phylospecTypeComponentName, String propertyName, StringBuilder objectBuilder) {
        return switch (phylospecTypeComponentName) {
            case "Tree" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".leafCount()");
                case "nBranches" -> objectBuilder.append(".branchCount()");
                case "nInternalNodes" -> new StringBuilder().append(objectBuilder).append(".nodeCount() - ").append(objectBuilder).append(".leafCount()");
                case "taxa" -> objectBuilder.append(".taxa()");
                default -> null;
            };
            case "TaxonSet" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".length()");
                case "names" -> objectBuilder.append(".taxaNames()");
                default -> null;
            };
            case "Alignment" -> switch (propertyName) {
                case "ntax" -> objectBuilder.append(".length()");
                case "nchar" -> objectBuilder.append(".nchar()");
                case "dataType" -> objectBuilder.append(".dataType()");
                case "taxa" -> objectBuilder.append(".taxa()");
                default -> null;
            };
            default -> null;
        };
    }
}
