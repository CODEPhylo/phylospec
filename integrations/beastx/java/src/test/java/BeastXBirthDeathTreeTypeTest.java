import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXBirthDeathTreeTypeTest {

    @Test
    public void usesUnscaledTreeDensityInDirectModelAndXml() throws Exception {
        String source = """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ BirthDeath(
                    diversificationRate=1.0,
                    turnover=0.25,
                    samplingProbability=0.9,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 1
                    Integer randomSeed = 1234
                    Logger screenLogger = screenLogger(logEvery=1)
                }
                """;

        BeastXModel model =
                XmlTestSupport.buildModel("birthDeathTreeType", source);

        SpeciationLikelihood treePrior =
                (SpeciationLikelihood) model.beastState.treePriorDistributions
                        .values()
                        .iterator()
                        .next();

        BirthDeathGernhard08Model actualModel =
                (BirthDeathGernhard08Model) treePrior.getSpeciationModel();

        BirthDeathGernhard08Model expectedUnscaledModel =
                new BirthDeathGernhard08Model(
                        new Parameter.Default(actualModel.getR()),
                        new Parameter.Default(actualModel.getA()),
                        new Parameter.Default(actualModel.getRho()),
                        BirthDeathGernhard08Model.TreeType.UNSCALED,
                        Units.Type.YEARS
                );

        assertEquals(
                expectedUnscaledModel.logTreeProbability(10),
                actualModel.logTreeProbability(10),
                1e-12,
                "PhyloSpec BirthDeath direct construction must use UNSCALED tree density"
        );

        Path xmlPath =
                XmlTestSupport.xmlPath("birthDeathTreeType");

        Files.createDirectories(XmlTestSupport.XML_OUTPUT_DIRECTORY);
        Files.deleteIfExists(xmlPath);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        assertTrue(
                xml.contains("<birthDeathModel id=\"tree_prior_model\" type=\"UNSCALED\""),
                "PhyloSpec BirthDeath XML must explicitly use UNSCALED tree density"
        );
    }
}
