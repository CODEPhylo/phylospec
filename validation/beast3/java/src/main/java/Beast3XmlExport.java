import beast.base.util.Randomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BEAST 3 validation entry point that exports a PhyloSpec model to XML.
 */
public final class Beast3XmlExport {

    private Beast3XmlExport() {
    }

    public static void main(String[] args) throws Exception {
        Path outputPrefix =
                ValidationConfiguration.requiredPath("outputPrefix");
        Path xmlPath =
                ValidationConfiguration.requiredPath("xml");
        Path operatorSummaryPath =
                ValidationConfiguration.optionalPath("operatorSummary");
        long seed =
                Long.parseLong(
                        ValidationConfiguration.required("seed")
                );

        if (outputPrefix.getParent() != null) {
            Files.createDirectories(outputPrefix.getParent());
        }
        if (xmlPath.getParent() != null) {
            Files.createDirectories(xmlPath.getParent());
        }
        if (operatorSummaryPath != null
                && operatorSummaryPath.getParent() != null) {
            Files.createDirectories(operatorSummaryPath.getParent());
        }

        String source =
                ValidationConfiguration.readSource("source");

        Randomizer.setSeed(seed);
        System.setProperty("java.only", "true");

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        runner.writeXml(
                outputPrefix.toString(),
                xmlPath,
                operatorSummaryPath
        );

        System.out.println(
                "Generated BEAST 3 XML: " + xmlPath
        );

        if (operatorSummaryPath != null) {
            System.out.println(
                    "Generated operator summary: "
                            + operatorSummaryPath
            );
        }
    }
}
