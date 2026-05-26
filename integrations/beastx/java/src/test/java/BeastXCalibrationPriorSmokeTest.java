import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXCalibrationPriorSmokeTest {

    @Test
    public void buildsRootAgeCalibrationPrior() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
        
                Rate birthRate ~ LogNormal(logMean=0.0, logSd=1.0)
        
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
        
                Age calibratedRoot = rootAge(tree=tree) observed between [1.0, 10.0]
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertEquals(1, summary.calibrationPriors.size());
        assertTrue(summary.calibrationPriors.contains("rootCalibration"));

        assertEquals(1, summary.treePriors.size());
        assertTrue(summary.treePriors.contains("tree_prior"));

        assertTrue(summary.operators.contains("NodeHeightScaleOperator"));
        assertTrue(summary.operators.contains("ExchangeOperator"));
        assertTrue(summary.operators.contains("WilsonBalding"));
    }

    @Test
    public void buildsMRCAAgeCalibrationPrior() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
    
                Rate birthRate ~ LogNormal(logMean=0.0, logSd=1.0)
    
                Tree tree ~ Yule(
                    birthRate=birthRate,
                    taxa=taxa
                )
    
                Age humanChimp = mrca(
                    clade=["Homo_sapiens", "Pan"],
                    tree=tree
                ) observed between [1.0, 10.0]
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertTrue(summary.calibrationPriors.contains("mrcaCalibration"));
        assertTrue(summary.treePriors.contains("tree_prior"));
        assertTrue(summary.operators.contains("NodeHeightScaleOperator"));
        assertTrue(summary.operators.contains("ExchangeOperator"));
    }
}