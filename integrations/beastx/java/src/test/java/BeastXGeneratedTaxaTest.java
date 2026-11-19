import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tiling.BeastXModel;
import tiling.mcmc.MCMCBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the LPhy-style {@code taxa(num=...)} and {@code taxa(names=..., species=..., ages=...)} generators. */
public class BeastXGeneratedTaxaTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Yule(birthRate=1.0, taxa=taxa(num=5))",
                "BirthDeath(diversificationRate=1.0, turnover=0.5, taxa=taxa(num=5))",
                "Coalescent(populationSize=100.0, taxa=taxa(num=5))"
            })
    public void treePriorsAcceptNumberedTaxa(String distribution) throws Exception {
        BeastXModel model =
                new PhyloSpecRunner("Tree simulated ~ " + distribution)
                        .buildModel("numberedTaxa");

        TreeModel tree =
                sampledTree(model);

        Map<String, Double> expected =
                IntStream.rangeClosed(1, 5)
                        .boxed()
                        .collect(Collectors.toMap(String::valueOf, i -> 0.0));

        assertEquals(expected, tipHeights(tree));

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            assertNull(tree.getNodeTaxon(tree.getExternalNode(i)).getDate(), "Numbered taxa should be undated.");
        }

        AbstractModelLikelihood prior =
                model.beastState.treePriorDistributions.get(tree);

        assertTrue(Double.isFinite(prior.getLogLikelihood()));
    }

    @Test
    public void namedTaxaKeepSpeciesAndAgesInOrder() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(
                        """
                        Tree simulated ~ Coalescent(
                            populationSize=100.0,
                            taxa=taxa(names=["a", "b", "c"], species=["x", "x", "y"], ages=[0.0, 1.0, 2.5])
                        )
                        """
                ).buildModel("namedTaxa");

        TreeModel tree =
                sampledTree(model);

        assertEquals(Map.of("a", 0.0, "b", 1.0, "c", 2.5), tipHeights(tree));

        Map<String, Object> species =
                new HashMap<>();

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            Taxon taxon =
                    tree.getNodeTaxon(tree.getExternalNode(i));

            species.put(taxon.getId(), taxon.getAttribute("species"));
        }

        assertEquals(Map.of("a", "x", "b", "x", "c", "y"), species);
    }

    @Test
    public void existingTreeOverloadStillResolves() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(
                        """
                        Tree reference = fromNewick("((a:1.0,b:1.0):1.0,c:2.0);")
                        Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(reference))
                        """
                ).buildModel("treeTaxa");

        assertEquals(Map.of("a", 0.0, "b", 0.0, "c", 0.0), tipHeights(sampledTree(model)));
    }

    @Test
    public void rejectsArgumentsOfTheWrongLength() {
        Exception error =
                assertThrows(
                        Exception.class,
                        () -> new PhyloSpecRunner(
                                "Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(names=[\"a\", \"b\"], ages=[0.0]))"
                        ).buildModel("wrongLength")
                );

        assertTrue(error.getMessage().contains("wrong length"), error.getMessage());
    }

    @Test
    public void rejectsDuplicateNames() {
        Exception error =
                assertThrows(
                        Exception.class,
                        () -> new PhyloSpecRunner(
                                "Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(names=[\"a\", \"a\"]))"
                        ).buildModel("duplicateNames")
                );

        assertTrue(error.getMessage().contains("Duplicate taxon name"), error.getMessage());
    }

    @Test
    public void runsDirectMCMCOverNumberedTaxa() throws Exception {
        BeastXModel model =
                new PhyloSpecRunner("Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(num=10))")
                        .buildModel("numberedTaxaDirectMCMC");

        MCMC mcmc =
                new MCMCBuilder(5).build(model);

        mcmc.run();
    }

    @Test
    public void exportsAndRunsXmlWithNumberedTaxa() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("numberedTaxa");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("numberedTaxa");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Tree simulated ~ Yule(birthRate=1.0, taxa=taxa(num=10))

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[simulated]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlNumberedTaxa", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        for (int i = 1; i <= 10; i++) {
            XmlTestSupport.assertXmlContains(xml, "<taxon id=\"" + i + "\"");
        }

        XmlTestSupport.runXml(xmlPath);

        String trees =
                Files.readString(treeLogPath);

        assertTrue(trees.contains("STATE_5"), trees);

        String taxlabels =
                trees.substring(trees.indexOf("Taxlabels") + "Taxlabels".length(), trees.indexOf(";", trees.indexOf("Taxlabels")));

        assertEquals(
                IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).collect(Collectors.toSet()),
                Set.of(taxlabels.trim().split("\\s+")),
                trees
        );
    }

    @Test
    public void exportsAndRunsXmlWithDatedNamedTaxa() throws Exception {
        Path xmlPath =
                XmlTestSupport.xmlPath("namedDatedTaxa");

        Path treeLogPath =
                XmlTestSupport.treeLogPath("namedDatedTaxa");

        XmlTestSupport.prepare(xmlPath, treeLogPath);

        String source =
                """
                Tree simulated ~ Coalescent(
                    populationSize=100.0,
                    taxa=taxa(names=["a", "b", "c"], ages=[0.0, 1.0, 2.0])
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234
                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[simulated]
                    )
                }
                """.formatted(XmlTestSupport.unixPath(treeLogPath));

        BeastXModel model =
                XmlTestSupport.buildModel("xmlNamedDatedTaxa", source);

        String xml =
                XmlTestSupport.writeXml(model, xmlPath);

        XmlTestSupport.assertXmlContains(xml, "<taxon id=\"c\">");
        XmlTestSupport.assertXmlContains(xml, "<date value=\"2.0\" direction=\"backwards\" units=\"years\"/>");

        XmlTestSupport.runXml(xmlPath);
    }

    private static TreeModel sampledTree(BeastXModel model) {
        assertEquals(1, model.beastState.treePriorDistributions.size());

        return model.beastState.treePriorDistributions
                .keySet()
                .iterator()
                .next();
    }

    private static Map<String, Double> tipHeights(TreeModel tree) {
        Map<String, Double> heights =
                new HashMap<>();

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef node =
                    tree.getExternalNode(i);

            // relative dates can yield -0.0 for the youngest tip
            heights.put(tree.getNodeTaxon(node).getId(), tree.getNodeHeight(node) + 0.0);
        }

        return heights;
    }
}
