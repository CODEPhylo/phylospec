package tiling.summary;

public record BeastXCapability(
        String area,
        String feature,
        String tileCoverage,
        String modelConstruction,
        String inMemoryMCMC,
        String xmlExport,
        String xmlParseRun,
        String notes
) {

    public BeastXCapability {
        area = requireText(area, "area");
        feature = requireText(feature, "feature");
        tileCoverage = requireText(tileCoverage, "tileCoverage");
        modelConstruction = requireText(modelConstruction, "modelConstruction");
        inMemoryMCMC = requireText(inMemoryMCMC, "inMemoryMCMC");
        xmlExport = requireText(xmlExport, "xmlExport");
        xmlParseRun = requireText(xmlParseRun, "xmlParseRun");
        notes = notes == null ? "" : notes;
    }

    public String toMarkdownRow() {
        return "| %s | %s | %s | %s | %s | %s | %s | %s |%n".formatted(
                escape(area),
                escape(feature),
                escape(tileCoverage),
                escape(modelConstruction),
                escape(inMemoryMCMC),
                escape(xmlExport),
                escape(xmlParseRun),
                escape(notes)
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }

        return value;
    }

    private static String escape(String value) {
        return value.replace("|", "\\|");
    }
}