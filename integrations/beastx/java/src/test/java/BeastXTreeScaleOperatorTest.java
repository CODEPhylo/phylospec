import dr.app.beast.BeastParser;
import dr.app.beast.BeastVersion;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.ScaleOperator;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.operators.OperatorBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeastXTreeScaleOperatorTest {

    @Test
    public void buildsConfiguredTreeScaleOperatorForDirectAndXmlPaths()
            throws Exception {
        String source = """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                mcmc {
                    Real treeScaleWeight = 9.0
                    Real treeScaleFactor = 0.4
                    Logger screenLogger = screenLogger(logEvery=1000)
                }
                """;

        BeastXModel model =
                XmlTestSupport.buildModel("treeScaleOperator", source);

        List<MCMCOperator> operators =
                new OperatorBuilder().build(model.beastState);

        ScaleOperator directTreeScale =
                operators.stream()
                        .filter(ScaleOperator.class::isInstance)
                        .map(ScaleOperator.class::cast)
                        .filter(operator ->
                                "tree.allInternalNodeHeights".equals(
                                        operator.getVariable().getId()
                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(9.0, directTreeScale.getWeight());
        assertEquals(0.4, directTreeScale.getScaleFactor());
        directTreeScale.doOperation();

        ScaleOperator directRootScale =
                operators.stream()
                        .filter(ScaleOperator.class::isInstance)
                        .map(ScaleOperator.class::cast)
                        .filter(operator ->
                                "tree.rootHeight".equals(operator.getVariable().getId())
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(5.0, directRootScale.getWeight());
        assertEquals(0.75, directRootScale.getScaleFactor());

        Path xmlPath =
                XmlTestSupport.xmlPath("treeScaleOperator");

        XmlTestSupport.prepare(xmlPath);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(
                xml,
                "<scaleOperator id=\"tree_scale\" scaleFactor=\"0.4\" weight=\"9.0\" "
                        + "scaleAll=\"true\" ignoreBounds=\"true\">"
        );

        XmlTestSupport.assertXmlContains(
                xml,
                "<parameter idref=\"tree.allInternalNodeHeights\"/>"
        );

        XmlTestSupport.assertXmlContains(
                xml,
                "<scaleOperator id=\"tree_rootScale\" scaleFactor=\"0.75\" "
                        + "weight=\"5.0\" scaleAll=\"false\" ignoreBounds=\"true\">"
        );

        XmlTestSupport.assertXmlContains(
                xml,
                "<parameter idref=\"tree.rootHeight\"/>"
        );

        try (var reader = Files.newBufferedReader(xmlPath)) {
            new BeastParser(
                    new String[0],
                    List.of(),
                    false,
                    false,
                    false,
                    BeastVersion.INSTANCE
            ).parse(reader, dr.inference.mcmc.MCMC.class);
        }
    }
}
