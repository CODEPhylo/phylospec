import dr.evomodel.speciation.BirthDeathSerialSamplingModel;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.xml.builders.TreePriorXmlBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXFossilizedBirthDeathSemanticsTest {

    @Test
    public void directRateModelHasAValidOriginWithoutRootAge() throws Exception {
        BeastXModel model =
                buildModel(
                        """
                        Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                        Tree tree ~ FossilizedBirthDeath(
                            speciationRate=1.0,
                            extinctionRate=0.2,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            taxa=taxa(data)
                        )
                        """);

        SpeciationLikelihood prior = treePrior(model);
        BirthDeathSerialSamplingModel fbd =
                (BirthDeathSerialSamplingModel) prior.getSpeciationModel();

        assertTrue(fbd.isSamplingOrigin());
        assertTrue(Double.isFinite(prior.getLogLikelihood()));
    }

    @Test
    public void diversificationTurnoverRatesRemainLinkedToTheirParameters() throws Exception {
        BeastXModel model =
                buildModel(
                        """
                        Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                        Rate diversificationRate ~ LogNormal(logMean=0.0, logSd=0.5)
                        Probability turnover ~ Beta(alpha=2.0, beta=8.0)
                        Tree tree ~ FossilizedBirthDeath(
                            diversificationRate=diversificationRate,
                            turnover=turnover,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            taxa=taxa(data)
                        )
                        """);

        Parameter diversificationRate =
                model.beastState.stateNodesByPhyloSpecName.get("diversificationRate");
        Parameter turnover =
                model.beastState.stateNodesByPhyloSpecName.get("turnover");
        BirthDeathSerialSamplingModel fbd =
                (BirthDeathSerialSamplingModel) treePrior(model).getSpeciationModel();

        diversificationRate.setParameterValue(0, 0.8);
        turnover.setParameterValue(0, 0.2);

        assertEquals(1.0, fbd.birth(), 1.0e-12);
        assertEquals(0.2, fbd.death(), 1.0e-12);

        turnover.setParameterValue(0, 0.5);

        assertEquals(1.6, fbd.birth(), 1.0e-12);
        assertEquals(0.8, fbd.death(), 1.0e-12);
    }

    @Test
    public void diversificationTurnoverXmlIsRejectedUntilDerivedParametersAreSupported()
            throws Exception {
        BeastXModel model =
                buildModel(
                        """
                        Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                        Rate diversificationRate ~ LogNormal(logMean=0.0, logSd=0.5)
                        Probability turnover ~ Beta(alpha=2.0, beta=8.0)
                        Tree tree ~ FossilizedBirthDeath(
                            diversificationRate=diversificationRate,
                            turnover=turnover,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            taxa=taxa(data)
                        )
                        """);

        UnsupportedOperationException error =
                assertThrows(
                        UnsupportedOperationException.class,
                        () ->
                                new TreePriorXmlBuilder()
                                        .buildModelDefinition(
                                                model.beastState,
                                                treePrior(model)
                                        )
                );

        assertTrue(error.getMessage().contains("diversificationRate/turnover"));
    }

    private static BeastXModel buildModel(String source) throws Exception {
        return new PhyloSpecRunner(source).buildModel("fbd-semantics-test");
    }

    private static SpeciationLikelihood treePrior(BeastXModel model) {
        return (SpeciationLikelihood)
                model.beastState.treePriorDistributions.values().iterator().next();
    }
}
