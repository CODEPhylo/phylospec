package org.phylospec.converters;

public class LPhyMethodsMapping {
    public static StringBuilder map(String componentName, String properyName, StringBuilder objectBuilder) {
        return switch (componentName) {
            case "Tree" -> switch (properyName) {
                case "ntax" -> objectBuilder.append(".leafCount()");
                case "nBranches" -> objectBuilder.append(".branchCount()");
                case "nInternalNodes" -> new StringBuilder().append(objectBuilder).append(".nodeCount() - ").append(objectBuilder).append(".leafCount()");
                case "taxa" -> objectBuilder.append(".taxa()");
                default -> null;
            };
            case "TaxonSet" -> switch (properyName) {
                case "ntax" -> objectBuilder.append(".length()");
                case "names" -> objectBuilder.append(".taxaNames()");
                default -> null;
            };
            case "Alignment" -> switch (properyName) {
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
