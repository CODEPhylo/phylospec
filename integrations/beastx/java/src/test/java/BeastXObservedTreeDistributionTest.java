import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.TreeModel;
import dr.inference.mcmc.MCMC;
import dr.inference.operators.MCMCOperator;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.mcmc.MCMCBuilder;
import tiling.operators.OperatorBuilder;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXObservedTreeDistributionTest {

    private static final String OBSERVED_YULE_SOURCE =
            """
            Tree treeData = fromNewick(
                "((A:1.0,B:1.0):1.0,(C:1.0,D:1.0):1.0);"
            )

            PositiveReal birthRate ~ LogNormal(
                logMean=0.0,
                logSd=1.0
            )

            Tree tree ~ Yule(
                birthRate=birthRate,
                taxa=taxa(treeData)
            ) observed as treeData
            """;

    private static final String OBSERVED_BIRTH_DEATH_SOURCE =
            """
            Tree treeData = fromNewick(
                "((A:1.0,B:1.0):1.0,(C:1.0,D:1.0):1.0);"
            )

            Tree tree ~ BirthDeath(
                diversificationRate=0.5,
                turnover=0.25,
                samplingProbability=0.9,
                taxa=taxa(treeData)
            ) observed as treeData
            """;

    @Test
    public void bindsYuleLikelihoodToFixedObservedTree() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(OBSERVED_YULE_SOURCE)
                        .buildModel("observedYule");

        assertTrue(model.beastState.treePriorDistributions.isEmpty());
        assertEquals(1, model.beastState.observedTreeDistributions.size());

        TreeModel observedTree =
                model.beastState.observedTreeDistributions
                        .keySet()
                        .iterator()
                        .next();

        SpeciationLikelihood likelihood =
                (SpeciationLikelihood) model.beastState.observedTreeDistributions
                        .get(observedTree);

        assertEquals("tree", observedTree.getId());
        assertEquals("tree_likelihood", likelihood.getId());
        assertSame(observedTree, likelihoodTree(likelihood));
        assertTrue(Double.isFinite(likelihood.getLogLikelihood()));
        assertEquals(1, model.likelihood.getLikelihoodCount());
        assertSame(likelihood, model.likelihood.getLikelihood(0));
    }

    @Test
    public void fixedObservedTreeDoesNotReceiveTreeOperators() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(OBSERVED_YULE_SOURCE)
                        .buildModel("observedYuleOperators");

        List<MCMCOperator> operators =
                new OperatorBuilder().build(model.beastState);

        assertEquals(1, operators.size(), "Only the sampled birth-rate parameter should receive an operator.");
        assertFalse(
                operators.stream().anyMatch(operator ->
                        operator.getClass().getPackageName().contains("evomodel.operators")),
                "A fixed observed tree must not receive tree operators."
        );
    }

    @Test
    public void runsDirectMCMCWithObservedTreeKeptFixed() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(OBSERVED_YULE_SOURCE)
                        .buildModel("observedYuleDirectMCMC");

        MCMC mcmc =
                new MCMCBuilder(5).build(model);

        assertEquals(1, mcmc.getOperatorSchedule().getOperatorCount());
        mcmc.run();
    }

    @Test
    public void bindsBirthDeathLikelihoodToFixedObservedTree() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(OBSERVED_BIRTH_DEATH_SOURCE)
                        .buildModel("observedBirthDeath");

        assertTrue(model.beastState.treePriorDistributions.isEmpty());
        assertEquals(1, model.beastState.observedTreeDistributions.size());

        TreeModel observedTree =
                model.beastState.observedTreeDistributions
                        .keySet()
                        .iterator()
                        .next();

        SpeciationLikelihood likelihood =
                (SpeciationLikelihood) model.beastState.observedTreeDistributions
                        .get(observedTree);

        assertSame(observedTree, likelihoodTree(likelihood));
        assertTrue(Double.isFinite(likelihood.getLogLikelihood()));
    }

    @Test
    public void exportsObservedYuleAsLikelihoodWithoutTreeOperators() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("observedYuleTree");

        XmlTestSupport.prepare(xmlPath);

        String source =
                OBSERVED_YULE_SOURCE
                        + """

                        mcmc {
                            Integer chainLength = 5
                            Integer randomSeed = 1234
                            Logger screenLogger = screenLogger(
                                logEvery=1,
                                parameters=[birthRate]
                            )
                        }
                        """;

        BeastXModel model =
                XmlTestSupport.buildModel("xmlObservedYuleTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<newick id=\"tree_startingTree\"");
        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<yuleModel id=\"tree_likelihood_model\"");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\">");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_likelihood\">");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<subtreeSlide");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<narrowExchange");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<wideExchange");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<wilsonBalding");

        XmlTestSupport.runXml(xmlPath);
    }

    @Test
    public void exportsObservedBirthDeathAsLikelihood() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("observedBirthDeathTree");

        XmlTestSupport.prepare(xmlPath);

        String source =
                """
                Tree treeData = fromNewick(
                    "((A:1.0,B:1.0):1.0,(C:1.0,D:1.0):1.0);"
                )

                PositiveReal diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Tree tree ~ BirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=0.25,
                    samplingProbability=0.9,
                    taxa=taxa(treeData)
                ) observed as treeData

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
                    Logger screenLogger = screenLogger(
                        logEvery=1,
                        parameters=[diversificationRate]
                    )
                }
                """;

        BeastXModel model =
                XmlTestSupport.buildModel("xmlObservedBirthDeathTree", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<birthDeathModel id=\"tree_likelihood_model\"");
        XmlTestSupport.assertXmlContains(xml, "<speciationLikelihood id=\"tree_likelihood\">");
        XmlTestSupport.assertXmlContains(xml, "<likelihood id=\"likelihood\">");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<subtreeSlide");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<narrowExchange");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<wideExchange");
        XmlTestSupport.assertXmlDoesNotContain(xml, "<wilsonBalding");

        XmlTestSupport.runXml(xmlPath);
    }

    private static TreeModel likelihoodTree(SpeciationLikelihood likelihood) throws Exception {
        Field field =
                SpeciationLikelihood.class.getDeclaredField("tree");

        field.setAccessible(true);

        return (TreeModel) field.get(likelihood);
    }
}
