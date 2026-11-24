import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Taxa;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.loggers.NumberColumn;
import dr.inference.mcmc.MCMC;
import dr.inference.model.Model;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accessors of BEAST X, checked against the alignment and tree they read.
 *
 * <p>Taxa and counts are compared with the input file. Tree statistics (rootAge, mrca and
 * age(node, tree)) are compared with node heights read from the tree, including after the tree
 * changes, and through the columns of the loggers, in memory and after an XML round trip.</p>
 */
class BeastXAccessorCoverageTest {

    private static final double TOLERANCE = 1e-10;

    private static final String NEXUS = "src/test/java/resources/three-taxa.nex";

    private static final String METADATA = "src/test/java/resources/taxon-metadata.nex";

    private static final String DATA = "Alignment data = fromNexus(file=\"" + NEXUS + "\")\n";

    /** Taxa of taxon-metadata.nex, named name_species_age_date. */
    private static final String A = "A_human_1.0_2019.0";
    private static final String B = "B_human_2.5_2017.5";
    private static final String C = "C_chimp_4.0_2016.0";

    @Test
    void taxaAndCountsMatchTheAlignment() {
        Taxa taxa = (Taxa) lastValue(DATA + "Taxa taxa = taxa(alignment=data)\n");
        assertEquals(List.of("A", "B", "C"), ids(taxa));

        assertEquals(3, ((IntScalar<?>) lastValue(DATA + "Integer n = numTaxa(alignment=data)\n")).get());
        assertEquals(8, ((IntScalar<?>) lastValue(DATA + "Integer n = numSites(alignment=data)\n")).get());
    }

    @Test
    void taxaOfATreeAreItsTips() {
        Taxa taxa = (Taxa) lastValue("Tree tree = fromNewick(newickString=\"((A:0.1,B:0.2):0.05,C:0.3);\")\n"
                + "Taxa taxa = taxa(tree=tree)\n");
        assertEquals(Set.of("A", "B", "C"), new HashSet<>(ids(taxa)));
    }

    /** Taxon names are name_species_age_date; ages 1.0, 2.5 and 4.0 are stored shifted to 0, 1.5, 3.0. */
    @Test
    void taxaCanBeIndexed() {
        String data = "Alignment data = fromNexus(file=\"" + METADATA + "\", age=parse(delimiter=\"_\", part=3), "
                + "speciesName=parse(delimiter=\"_\", part=2))\nTaxa taxa = taxa(alignment=data)\n";

        assertEquals(B, lastValue(data + "String n = name(taxon=taxa[2])\n"));
        assertEquals("chimp", lastValue(data + "String s = species(taxon=taxa[3])\n"));
        assertEquals(3.0, ((RealScalar<?>) lastValue(data + "Real a = age(taxon=taxa[3])\n")).get(), TOLERANCE);

        Exception tooSmall = assertThrows(Exception.class, () -> lastValue(data + "String n = name(taxon=taxa[0])\n"));
        assertTrue(tooSmall.getMessage().contains("smaller than 1"), tooSmall.getMessage());
        Exception tooLarge = assertThrows(Exception.class, () -> lastValue(data + "String n = name(taxon=taxa[4])\n"));
        assertTrue(tooLarge.getMessage().contains("greater than the number of elements"), tooLarge.getMessage());
    }

    /** Like BEAST 3, the youngest taxon is at height 0; the species is kept. */
    @Test
    void aListOfTaxaGivesTheTipHeights() throws Exception {
        String source = """
                Taxa taxa = [taxon(name="a", age=1.0), taxon(name="b", species="x", age=2.5), taxon(name="c", age=4.0)]
                Tree tree ~ Coalescent(populationSize=1.0, taxa=taxa)
                """;
        Map<String, Double> expected = Map.of("a", 0.0, "b", 1.5, "c", 3.0);

        TreeModel tree = new PhyloSpecRunner(source).buildState("accessors").treeModelsByPhyloSpecName.get("tree");
        assertTipHeights("in memory", tree, expected);
        assertEquals("x", tree.getTaxon(tree.getTaxonIndex("b")).getAttribute("species"));

        MCMC mcmc = xmlMcmc("taxonList", source + """

                mcmc {
                    Integer chainLength = 1
                    Logger treeLogger = treeLogger(logEvery=1, file="%s", trees=[tree])
                }
                """.formatted(XmlTestSupport.unixPath(XmlTestSupport.treeLogPath("accessors-taxonList"))));
        assertTipHeights("from XML", findModel(mcmc.getLikelihood().getModel(), TreeModel.class), expected);
    }

    @Test
    void aListOfTaxaWithARepeatedNameIsRejected() {
        Exception error = assertThrows(Exception.class, () -> new PhyloSpecRunner("""
                Taxa taxa = [taxon(name="a"), taxon(name="a", age=1.0)]
                Tree tree ~ Coalescent(populationSize=1.0, taxa=taxa)
                """).buildState("accessors"));
        assertTrue(error.getMessage().contains("appears twice"), error.getMessage());
    }

    /**
     * rootAge, mrca and age(node, tree) follow the tree: their values equal node heights read from
     * the tree, also after the tree changes, and they are logged under their PhyloSpec names.
     *
     * <p>Dated taxa (tips at 0, 1.5 and 3.0) make age(node, tree) non-zero, and the MRCA of all
     * three pairs is logged, so at least one MRCA is not the root.</p>
     */
    @Test
    void treeStatisticsFollowTheTreeAndAreLogged() throws Exception {
        String source = "Alignment data = fromNexus(file=\"" + METADATA + "\", age=parse(delimiter=\"_\", part=3))\n"
                + "Tree tree ~ Coalescent(populationSize=1.0, taxa=taxa(alignment=data))\n"
                + "Age root = rootAge(tree=tree)\n"
                + "Age ab = mrca(clade=[\"" + A + "\", \"" + B + "\"], tree=tree)\n"
                + "Age ac = mrca(clade=[\"" + A + "\", \"" + C + "\"], tree=tree)\n"
                + "Age bc = mrca(clade=[\"" + B + "\", \"" + C + "\"], tree=tree)\n"
                + "Age tip = age(node=\"" + C + "\", tree=tree)\n";
        String[] logged = {"root", "ab", "ac", "bc", "tip"};

        MCMC memory = runner(source + logger(logged, "accessors-memory")).buildMCMC("accessors", 1);
        TreeModel tree = findModel(memory.getLikelihood().getModel(), TreeModel.class);
        assertStatistics("in memory", memory, tree);

        tree.beginTreeEdit();
        tree.setNodeHeight(tree.getRoot(), tree.getNodeHeight(tree.getRoot()) + 0.7);
        tree.endTreeEdit();
        assertStatistics("in memory after the root moved", memory, tree);

        MCMC xml = xmlMcmc("treeStatistics", source + logger(logged, "accessors-treeStatistics"));
        assertStatistics("from XML", xml, findModel(xml.getLikelihood().getModel(), TreeModel.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Age a = mrca(clade=[\"A\", \"human\"], tree=tree)",
            "Age a = age(node=\"human\", tree=tree)",
            "Age a = mrca(clade=[\"A\", \" \"], tree=tree)"
    })
    void onlyExactTipNamesAreAccepted(String statement) {
        // As in BEAST 3: species names (here "human") and blank names are rejected.
        Exception error = assertThrows(Exception.class, () -> new PhyloSpecRunner(
                "Alignment data = fromNexus(file=\"" + NEXUS + "\")\n"
                        + "Tree tree ~ Yule(birthRate=1.0, taxa=taxa(alignment=data))\n" + statement + "\n"
        ).buildState("accessors"));
        assertTrue(error.getMessage().contains("is not a tip of the tree")
                || error.getMessage().contains("must not be blank"), error.getMessage());
    }

    private static void assertStatistics(String name, MCMC mcmc, TreeModel tree) {
        Map<String, Double> columns = loggedValues(mcmc);
        NodeRef a = tip(tree, A);
        NodeRef b = tip(tree, B);
        NodeRef c = tip(tree, C);

        assertEquals(tree.getNodeHeight(tree.getRoot()), columns.get("root"), TOLERANCE, name + " rootAge");
        assertEquals(tree.getNodeHeight(commonAncestor(tree, a, b)), columns.get("ab"), TOLERANCE, name + " mrca(A, B)");
        assertEquals(tree.getNodeHeight(commonAncestor(tree, a, c)), columns.get("ac"), TOLERANCE, name + " mrca(A, C)");
        assertEquals(tree.getNodeHeight(commonAncestor(tree, b, c)), columns.get("bc"), TOLERANCE, name + " mrca(B, C)");
        assertEquals(3.0, tree.getNodeHeight(c), TOLERANCE, name + " tip height of C");
        assertEquals(tree.getNodeHeight(c), columns.get("tip"), TOLERANCE, name + " age(node, tree)");
    }

    /** Values of the number columns of all loggers, by column label. */
    private static Map<String, Double> loggedValues(MCMC mcmc) {
        Map<String, Double> values = new HashMap<>();
        for (Logger logger : mcmc.getLoggers()) {
            if (logger instanceof MCLogger mcLogger) {
                for (int i = 0; i < mcLogger.getColumnCount(); i++) {
                    if (mcLogger.getColumn(i) instanceof NumberColumn column) {
                        values.put(column.getLabel(), column.getDoubleValue());
                    }
                }
            }
        }
        return values;
    }

    private static NodeRef commonAncestor(Tree tree, NodeRef first, NodeRef second) {
        Set<NodeRef> ancestors = new HashSet<>();
        for (NodeRef node = first; node != null; node = tree.getParent(node)) {
            ancestors.add(node);
        }
        NodeRef node = second;
        while (!ancestors.contains(node)) {
            node = tree.getParent(node);
        }
        return node;
    }

    private static void assertTipHeights(String name, Tree tree, Map<String, Double> expected) {
        expected.forEach((taxon, height) ->
                assertEquals(height, tree.getNodeHeight(tip(tree, taxon)), TOLERANCE, name + " tip " + taxon));
    }

    private static NodeRef tip(Tree tree, String taxon) {
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            if (tree.getNodeTaxon(tree.getExternalNode(i)).getId().equals(taxon)) {
                return tree.getExternalNode(i);
            }
        }
        throw new AssertionError("No tip " + taxon);
    }

    private static List<String> ids(Taxa taxa) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            ids.add(taxa.getTaxonId(i));
        }
        return ids;
    }

    private static String logger(String[] names, String fileName) {
        return """

                mcmc {
                    Integer chainLength = 1
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[%s])
                }
                """.formatted(XmlTestSupport.unixPath(XmlTestSupport.logPath(fileName)), String.join(", ", names));
    }

    private static PhyloSpecRunner runner(String source) {
        return new PhyloSpecRunner(source);
    }

    /** Builds the MCMC of a source with an mcmc block from its XML. */
    private static MCMC xmlMcmc(String name, String source) throws Exception {
        String fileName = "accessors-" + name;
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        XmlTestSupport.prepare(xmlPath, XmlTestSupport.logPath(fileName), XmlTestSupport.treeLogPath(fileName));

        try {
            MCMC mcmc = runner(source)
                    .buildXmlRun(XmlRunnerOptions.builder("accessors", xmlPath).build())
                    .mcmc();
            assertNotNull(mcmc);
            return mcmc;
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    private static <T> T findModel(Model model, Class<T> type) {
        if (type.isInstance(model)) {
            return type.cast(model);
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            T found = findModel(model.getModel(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Tiles the source and returns the value of its last statement. */
    private static Object lastValue(String source) {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BeastXState> evaluateTiles = new EvaluateTiles<>(
                new BeastXCoreTileLibrary().getTiles(),
                new VariableResolver(statements),
                stochasticityResolver
        );

        BeastXState state = new BeastXState("test");
        Object last = null;
        for (Tile<?, BeastXState> tile : evaluateTiles.getBestTiling(statements)) {
            last = tile.apply(state, new IdentityHashMap<>());
        }
        return last;
    }
}
