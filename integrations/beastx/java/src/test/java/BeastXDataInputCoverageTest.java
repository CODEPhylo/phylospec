import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data input and parsing generators of BEAST X, checked against the contents of the input files.
 *
 * <p>Alignments and fixed trees are checked through the JC69 likelihood, compared with Felsenstein
 * pruning on the expected sequences, and through the tip-to-tip distances of the tree. Sampling
 * ages are checked through the tip heights. Both are checked in memory and after an XML round
 * trip. Values that only exist while the model is built (strings, CSV rows, parsers) are checked
 * directly.</p>
 */
class BeastXDataInputCoverageTest {

    private static final double TOLERANCE = 1e-8;

    private static final String RESOURCES = "src/test/java/resources/";

    /** The sequences of three-taxa.nex and three-taxa.fasta. */
    private static final Map<String, String> SEQUENCES = Map.of(
            "A", "ACGTACGA",
            "B", "ACGTTCGA",
            "C", "AGGTACCA"
    );

    private static final String NEXUS = "Alignment data = fromNexus(file=\"" + RESOURCES + "three-taxa.nex\")\n";

    private static final String NEWICK_TREE = "Tree tree = fromNewick(newickString=\"((A:0.1,B:0.2):0.05,C:0.3);\")\n";

    /** Tip-to-tip distances of ((A:0.1,B:0.2):0.05,C:0.3). */
    private static final Map<String, Double> DISTANCES = Map.of("A-B", 0.3, "A-C", 0.45, "B-C", 0.55);

    static Stream<Arguments> alignmentsAndTrees() {
        return Stream.of(
                Arguments.of("fromNexus, fromNewick", NEXUS, NEWICK_TREE, SEQUENCES),
                Arguments.of("fromFasta",
                        "Alignment data = fromFasta(file=\"" + RESOURCES + "three-taxa.fasta\")\n",
                        NEWICK_TREE, SEQUENCES),
                Arguments.of("fromTree, Newick file", NEXUS,
                        "Tree tree = fromTree(file=\"" + RESOURCES + "three-taxa.newick\")\n", SEQUENCES),
                Arguments.of("fromTree, Nexus file", NEXUS,
                        "Tree tree = fromTree(file=\"" + RESOURCES + "three-taxa-tree.nex\")\n", SEQUENCES),
                Arguments.of("subset, start and end",
                        "Alignment all = fromNexus(file=\"" + RESOURCES + "three-taxa.nex\")\n"
                                + "Alignment data = subset(alignment=all, start=2, end=7)\n",
                        NEWICK_TREE, sites(2, 3, 4, 5, 6, 7)),
                Arguments.of("subset, codonPosition",
                        "Alignment all = fromNexus(file=\"" + RESOURCES + "three-taxa.nex\")\n"
                                + "Alignment data = subset(alignment=all, codonPosition=2)\n",
                        NEWICK_TREE, sites(2, 5, 8))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("alignmentsAndTrees")
    void alignmentsAndFixedTreesMatchTheirFiles(String name, String data, String tree, Map<String, String> sequences)
            throws Exception {
        String source = data + tree + "Alignment x ~ PhyloCTMC(tree=tree, qMatrix=jc69()) observed as data\n";

        assertMatches(name + " in memory", findLikelihood(buildModel(source).likelihood), sequences);
        assertMatches(name + " from XML", findLikelihood(parseXml(name, source)), sequences);
    }

    static Stream<Arguments> sampledAges() {
        String nexus = RESOURCES + "taxon-metadata.nex";
        String fasta = RESOURCES + "taxon-metadata.fasta";
        return Stream.of(
                Arguments.of("fromNexus, age", "fromNexus(file=\"" + nexus + "\", age=parse(delimiter=\"_\", part=3))"),
                Arguments.of("fromNexus, date", "fromNexus(file=\"" + nexus + "\", date=parse(delimiter=\"_\", part=4))"),
                Arguments.of("fromFasta, age",
                        "fromFasta(file=\"" + fasta + "\", age=parse(regex=\"_([0-9.]+)_[0-9.]+$\"))"),
                Arguments.of("fromFasta, date", "fromFasta(file=\"" + fasta + "\", date=parse(regex=\"_([0-9.]+)$\"))")
        );
    }

    /**
     * Taxon names are name_species_age_date: ages 1.0, 2.5, 4.0 and dates 2019.0, 2017.5, 2016.0. Both
     * give tip heights 0, 1.5 and 3.0: like BEAST 3, the youngest tip is at height 0.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("sampledAges")
    void tipHeightsFollowTheParsedAgesAndDates(String name, String alignment) throws Exception {
        String source = "Alignment data = " + alignment + "\n"
                + "Tree tree ~ Coalescent(populationSize=1.0, taxa=taxa(data))\n";
        Map<String, Double> expected = Map.of("A", 0.0, "B", 1.5, "C", 3.0);

        assertTipHeights(name + " in memory", buildState(source).treeModelsByPhyloSpecName.get("tree"), expected);
        assertTipHeights(name + " from XML", findModel(parseXml(name, source).getModel(), TreeModel.class), expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("speciesNameReaders")
    void speciesNamesAreParsedFromTheTaxonNames(String reader) throws Exception {
        TreeModel tree = buildState("Alignment data = " + reader + "\n"
                + "Tree tree ~ Coalescent(populationSize=1.0, taxa=taxa(data))\n").treeModelsByPhyloSpecName.get("tree");

        Map<String, Object> species = new HashMap<>();
        for (int i = 0; i < tree.getTaxonCount(); i++) {
            species.put(tree.getTaxon(i).getId().substring(0, 1), tree.getTaxon(i).getAttribute("species"));
        }
        assertEquals(Map.of("A", "human", "B", "human", "C", "chimp"), species);
    }

    static Stream<String> speciesNameReaders() {
        return Stream.of(
                "fromNexus(file=\"" + RESOURCES + "taxon-metadata.nex\", speciesName=parse(delimiter=\"_\", part=2))",
                "fromFasta(file=\"" + RESOURCES + "taxon-metadata.fasta\", speciesName=parse(delimiter=\"_\", part=2))"
        );
    }

    /** No core overload takes both, so the type checker rejects it before BEAST X sees it. */
    @Test
    void ageAndDateTogetherAreRejected() {
        Exception error = assertThrows(Exception.class, () -> buildState("Alignment data = fromFasta(file=\""
                + RESOURCES + "taxon-metadata.fasta\", age=parse(delimiter=\"_\", part=3), "
                + "date=parse(delimiter=\"_\", part=4))\n"));
        assertTrue(error.getMessage().contains("does not exist"), error.getMessage());
    }

    @Test
    void nameAndSpeciesReadTheTaxon() {
        assertEquals("a", lastValue("String n = name(taxon=taxon(name=\"a\", species=\"human\"))\n"));
        assertEquals("human", lastValue("String s = species(taxon=taxon(name=\"a\", species=\"human\"))\n"));

        Exception error = assertThrows(Exception.class, () -> lastValue("String s = species(taxon=taxon(name=\"a\"))\n"));
        assertTrue(error.getMessage().contains("no species name"), error.getMessage());
    }

    @Test
    void envReadsTheEnvironmentVariable() {
        assertEquals(System.getenv("PATH"), lastValue("String v = env(variable=\"PATH\")\n"));

        Exception error = assertThrows(Exception.class,
                () -> lastValue("String v = env(variable=\"PHYLOSPEC_SURELY_UNSET_VARIABLE\")\n"));
        assertTrue(error.getMessage().contains("is not set"), error.getMessage());
    }

    /** Like BEAST 3: quoted fields keep delimiters, doubled quotes and line breaks; values are not trimmed. */
    @Test
    void csvRowsKeepQuotedFieldsAndWhitespace(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("metadata.csv");
        Files.writeString(file, "﻿taxon,description,empty\r\n\r\n"
                + "sampleA,\"a,b and \"\"quoted\"\"\",\r\n"
                + "sampleB,\"first\r\nsecond\",  untrimmed  \r\n");
        String table = "Vector<Map<String, String>> m = fromCSV(file=\"" + unixPath(file) + "\")\n";

        assertEquals(
                List.of(
                        Map.of("taxon", "sampleA", "description", "a,b and \"quoted\"", "empty", ""),
                        Map.of("taxon", "sampleB", "description", "first\r\nsecond", "empty", "  untrimmed  ")),
                lastValue(table));
        assertEquals("sampleB", lastValue(table + "String v = m[2][\"taxon\"]\n"));

        Exception missing = assertThrows(Exception.class, () -> lastValue(table + "String v = m[1][\"species\"]\n"));
        assertTrue(missing.getMessage().contains("no field 'species'"), missing.getMessage());
    }

    @Test
    void csvWithADuplicateHeaderIsRejected(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("duplicate.csv");
        Files.writeString(file, "taxon,taxon\nsampleA,spA\n");

        Exception error = assertThrows(Exception.class,
                () -> lastValue("Vector<Map<String, String>> m = fromCSV(file=\"" + unixPath(file) + "\")\n"));
        assertTrue(error.getMessage().contains("appears twice"), error.getMessage());
    }

    private static void assertMatches(String name, Likelihood likelihood, Map<String, String> sequences) {
        TreeModel tree = findModel(likelihood.getModel(), TreeModel.class);
        DISTANCES.forEach((pair, distance) -> assertEquals(distance,
                distance(tree, pair.substring(0, 1), pair.substring(2)), TOLERANCE, name + " distance " + pair));
        assertEquals(JukesCantorPruning.logLikelihood(tree, sequences, node -> 1.0, JukesCantorPruning.ONE_CATEGORY),
                likelihood.getLogLikelihood(), TOLERANCE, name + " log-likelihood");
    }

    private static void assertTipHeights(String name, Tree tree, Map<String, Double> expected) {
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef tip = tree.getExternalNode(i);
            String taxon = tree.getNodeTaxon(tip).getId().substring(0, 1);
            assertEquals(expected.get(taxon), tree.getNodeHeight(tip), TOLERANCE, name + " tip " + taxon);
        }
    }

    /** Sum of the branch lengths on the path between two tips. */
    private static double distance(Tree tree, String first, String second) {
        NodeRef a = tip(tree, first);
        NodeRef b = tip(tree, second);
        double distance = 0.0;
        while (a != b) {
            if (tree.getNodeHeight(a) <= tree.getNodeHeight(b)) {
                distance += tree.getBranchLength(a);
                a = tree.getParent(a);
            } else {
                distance += tree.getBranchLength(b);
                b = tree.getParent(b);
            }
        }
        return distance;
    }

    private static NodeRef tip(Tree tree, String taxon) {
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            if (tree.getNodeTaxon(tree.getExternalNode(i)).getId().equals(taxon)) {
                return tree.getExternalNode(i);
            }
        }
        throw new AssertionError("No tip " + taxon);
    }

    /** The given one-based sites of each sequence. */
    private static Map<String, String> sites(int... sites) {
        Map<String, String> subset = new HashMap<>();
        SEQUENCES.forEach((taxon, sequence) -> {
            StringBuilder selected = new StringBuilder();
            for (int site : sites) {
                selected.append(sequence.charAt(site - 1));
            }
            subset.put(taxon, selected.toString());
        });
        return subset;
    }

    private static BeastXState buildState(String source) throws Exception {
        return new PhyloSpecRunner(source).buildState("dataInput");
    }

    private static tiling.BeastXModel buildModel(String source) throws Exception {
        try {
            return new PhyloSpecRunner(source).buildMaterializedModel("dataInput");
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    /** Runs the source from XML; z is only there so that the run has something to log. */
    private static Likelihood parseXml(String name, String source) throws Exception {
        String fileName = "dataInput-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path logPath = XmlTestSupport.logPath(fileName);
        XmlTestSupport.prepare(xmlPath, logPath);

        try {
            return new PhyloSpecRunner(source + """
                    PositiveReal z ~ Exponential(rate=1.0)

                    mcmc {
                        Integer chainLength = 1
                        Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[z])
                    }
                    """.formatted(XmlTestSupport.unixPath(logPath)))
                    .buildXmlRun(XmlRunnerOptions.builder("dataInput", xmlPath).build())
                    .mcmc()
                    .getLikelihood();
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    /** Finds the tree likelihood of the alignment x among nested compound likelihoods. */
    private static Likelihood findLikelihood(Likelihood likelihood) {
        if ("x_likelihood".equals(likelihood.getId())) {
            return likelihood;
        }
        if (likelihood instanceof CompoundLikelihood compound) {
            for (Likelihood child : compound.getLikelihoods()) {
                Likelihood found = findLikelihood(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private static String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
