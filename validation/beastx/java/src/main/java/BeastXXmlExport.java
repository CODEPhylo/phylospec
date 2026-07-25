import tiling.BeastXModel;
import tiling.BeastXState;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BEAST X validation entry point that exports a PhyloSpec model to XML.
 */
public final class BeastXXmlExport {

    private BeastXXmlExport() {
    }

    public static void main(String[] args) throws Exception {
        String runName =
                ValidationConfiguration.required("runName");
        Path outputPrefix =
                ValidationConfiguration.requiredPath("outputPrefix");
        Path xmlPath =
                ValidationConfiguration.requiredPath("xml");
        Path operatorSummaryPath =
                ValidationConfiguration.optionalPath("operatorSummary");
        long seed =
                ValidationConfiguration.requiredLong("seed");
        long logEvery =
                ValidationConfiguration.requiredLong("logEvery");

        if (outputPrefix.getParent() != null) {
            Files.createDirectories(outputPrefix.getParent());
        }
        if (xmlPath.getParent() != null) {
            Files.createDirectories(xmlPath.getParent());
        }

        String source =
                ValidationConfiguration.readSource("source");

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);
        BeastXState state =
                runner.buildState(runName);

        BeastXValidationSupport.configure(
                state,
                outputPrefix,
                seed,
                logEvery
        );
        BeastXValidationSupport.writeOperatorSummary(
                state,
                operatorSummaryPath
        );

        BeastXModel model =
                runner.buildModel(state);

        runner.writeXml(
                model,
                xmlPath
        );

        if (!Files.isRegularFile(xmlPath)
                || Files.size(xmlPath) == 0) {
            throw new IllegalStateException(
                    "Expected BEAST X XML was not generated: "
                            + xmlPath
            );
        }

        System.out.println(
                "Generated BEAST X XML: " + xmlPath
        );

        if (operatorSummaryPath != null) {
            System.out.println(
                    "Generated operator summary: "
                            + operatorSummaryPath
            );
        }
    }
}
