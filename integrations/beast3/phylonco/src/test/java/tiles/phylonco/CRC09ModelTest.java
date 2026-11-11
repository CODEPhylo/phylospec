package tiles.phylonco;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import phylonco.beast.evolution.likelihood.ReadCountTreeLikelihood;
import phylonco.beast.evolution.readcountmodel.LikelihoodReadCountModel;
import runner.PhyloSpecException;
import runner.PhyloSpecRunner;

/** The CRC09 model structure on a small synthetic data set (6 cells, 30 sites). */
class CRC09ModelTest {

    private static final List<String> LIBRARIES = List.of("phylonco", "beast2");

    /** Same statements as examples/CRC09.phylospec, with 6 cells and 2 skyline groups. */
    private static final String MODEL = """
            use phylonco

            data {
                Vector<String> cellNames = ["c1", "c2", "c3", "c4", "c5", "c6"]
                ReadCount readCounts = fromMpileup(file="%s", names=cellNames)
                Taxa taxa = taxa(readCounts)
                PositiveInteger numCells = numTaxa(readCounts)
            }

            model {
                Simplex genotypeFrequencies ~ Dirichlet(
                    concentration=[3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0]
                )
                Simplex relativeRates ~ Dirichlet(concentration=[2.0, 20.0, 2.0, 2.0, 20.0, 2.0])
                QMatrix qMatrix = gt10(relativeRates=relativeRates, genotypeFrequencies=genotypeFrequencies)

                PositiveInteger numGroups = 2
                Vector<PositiveInteger> groupSizes ~ RandomComposition(total=numCells - 1, numParts=numGroups)
                PositiveReal initialMean ~ LogNormal(logMean=-5.0, logSd=2.0)
                Vector<PositiveReal> populationSizes ~ ExponentialMarkovChain(initialMean=initialMean, numValues=numGroups)
                Tree tree ~ SkylineCoalescent(populationSizes=populationSizes, groupSizes=groupSizes, taxa=taxa)

                Probability epsilon ~ Beta(alpha=1.0, beta=30.0)
                Probability delta ~ Beta(alpha=1.0, beta=4.0)
                PositiveReal t ~ LogNormal(logMean=1.7, logSd=0.05)
                PositiveReal v ~ LogNormal(logMean=2.96, logSd=0.13)
                PositiveReal w1 ~ LogNormal(logMean=0.1, logSd=0.5)
                PositiveReal w2 ~ LogNormal(logMean=1.0, logSd=0.8)
                Vector<PositiveReal> s ~ IID(base=LogNormal(logMean=0.2, logSd=0.72), num=numCells)

                ReadCount reads ~ ReadCountModel(
                    tree=tree,
                    qMatrix=qMatrix,
                    sequencingError=epsilon,
                    allelicDropout=delta,
                    meanAllelicCoverage=t,
                    allelicCoverageVariance=v,
                    cellSizeFactors=s,
                    homozygousOverdispersion=w1,
                    heterozygousOverdispersion=w2
                ) observed as readCounts
            }
            """;

    /** LPhy's explicit form of the observation model: latent genotypes, ploidy and coverage. */
    private static final String EXPLICIT_READS = """
            PositiveInteger numSites = numSites(readCounts)
            Alignment genotypes ~ PhyloCTMC(tree=tree, qMatrix=qMatrix, numSites=numSites)
            Matrix<PositiveInteger> ploidy ~ Ploidy(numCells=numCells, numSites=numSites, allelicDropout=delta)
            Matrix<NonNegativeInteger> coverage ~ CoverageModel(
                ploidy=ploidy, meanAllelicCoverage=t, allelicCoverageVariance=v, cellSizeFactors=s
            )
            ReadCount reads ~ ReadCountModel(
                genotypes=genotypes,
                ploidy=ploidy,
                coverage=coverage,
                sequencingError=epsilon,
                homozygousOverdispersion=w1,
                heterozygousOverdispersion=w2
            )""";

    /** {@link #MODEL} with its compact ReadCountModel statement replaced by the explicit form. */
    private static String model(boolean explicit) {
        if (!explicit) return MODEL;
        int start = MODEL.indexOf("ReadCount reads ~ ReadCountModel(");
        int end = MODEL.indexOf(" observed as readCounts");
        return MODEL.substring(0, start) + EXPLICIT_READS + MODEL.substring(end);
    }

    @TempDir
    Path directory;

    private Path data() throws Exception {
        Path target = this.directory.resolve("six-cells.mpileup");
        if (!Files.exists(target)) {
            try (var in = getClass().getResourceAsStream("/six-cells.mpileup")) {
                Files.copy(in, target);
            }
        }
        return target;
    }

    @Test
    void checkHighlightsMisspelledReadCountArgument() throws Exception {
        String source = MODEL.formatted(data()).replace("sequencingError=epsilon", "seqError=epsilon");
        var error = assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, LIBRARIES).check())
                .diagnostic().error();

        assertEquals("Do you mean 'sequencingError'?", error.hint());
        var range = error.range();
        assertEquals(range.startLine, range.endLine);
        String line = source.lines().toList().get(range.startLine - 1);
        assertEquals("seqError", line.substring(range.start, range.end));
    }

    @Test
    void checkSuggestsImportForReadCount() throws Exception {
        String source = MODEL.formatted(data()).replace("use phylonco\n", "");
        var error = assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, LIBRARIES).check())
                .diagnostic().error();

        assertTrue(error.description().contains("ReadCount"), error::description);
        assertTrue(error.hint().contains("use phylonco"), error::hint);
    }

    @Test
    void checkExplainsHowToLoadAMissingAdapter() throws Exception {
        String source = MODEL.formatted(data());
        var error = assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, List.of("beast2"))
                        .check())
                .diagnostic()
                .error();

        assertTrue(error.description().contains("'phylonco'"), error::description);
        assertTrue(error.hint().contains("--adapter-path"), error::hint);
        assertTrue(error.hint().contains("--library phylonco"), error::hint);
        assertFalse(error.hint().contains("use phylospec"), error::hint);
    }

    /** The shipped examples must keep passing the checker; the CRC09 data are not needed for that. */
    @ParameterizedTest
    @ValueSource(strings = {"CRC09.phylospec", "CRC09-explicit.phylospec"})
    void examplesPassTheChecker(String example) throws Exception {
        new PhyloSpecRunner(Path.of("examples", example), LIBRARIES).check();
    }

    @ParameterizedTest(name = "explicit={0}")
    @ValueSource(booleans = {false, true})
    void readCountModelBecomesOneReadCountTreeLikelihood(boolean explicit) throws Exception {
        BEASTState state = evaluate(model(explicit).formatted(data()));

        assertEquals(1, state.likelihoodDistributions.size(), state.likelihoodDistributions.toString());
        Distribution likelihood = state.likelihoodDistributions.getFirst();
        ReadCountTreeLikelihood treeLikelihood = assertInstanceOf(ReadCountTreeLikelihood.class, likelihood);
        LikelihoodReadCountModel readCountModel = treeLikelihood.readCountModelInput.get();
        assertTrue(state.priorDistributions.values().stream().noneMatch(prior -> prior == readCountModel),
                "the read-count model must not be counted a second time");
        assertEquals(6, readCountModel.readCountInput.get().getTaxaNumber());
        assertEquals(30, readCountModel.readCountInput.get().getSiteNumber());

        double logP = treeLikelihood.calculateLogP();
        assertTrue(Double.isFinite(logP) && logP < 0.0, "log likelihood " + logP);
    }

    @Test
    void explicitFormIntegratesTheLatentDrawsOut() throws Exception {
        Set<String> compact = stateNodeIds(evaluate(model(false).formatted(data())));
        Set<String> explicit = stateNodeIds(evaluate(model(true).formatted(data())));

        // genotypes, ploidy and coverage are integrated out, so both forms sample the same nodes
        assertEquals(compact, explicit);
        assertFalse(explicit.contains("genotypes") || explicit.contains("ploidy") || explicit.contains("coverage"),
                explicit::toString);
    }

    @Test
    void latentGenotypesCannotBeUsedOutsideTheReadCountModel() throws Exception {
        String source = model(true).formatted(data())
                .replace("ReadCount reads ~", "PositiveInteger genotypeSites = numSites(genotypes)\n    ReadCount reads ~");

        assertThrows(PhyloSpecException.class, () -> new PhyloSpecRunner(source, LIBRARIES).check());
    }

    @Test
    void coverageMustUseTheSamePloidyAsTheReadCountModel() throws Exception {
        String source = model(true).formatted(data())
                .replace("Matrix<NonNegativeInteger> coverage", """
                        Matrix<PositiveInteger> otherPloidy ~ Ploidy(numCells=numCells, numSites=numSites, allelicDropout=delta)
                        Matrix<NonNegativeInteger> coverage""")
                .replace("ploidy=ploidy, meanAllelicCoverage", "ploidy=otherPloidy, meanAllelicCoverage");

        var error = assertThrows(TileApplicationError.class, () -> evaluate(source));
        assertTrue(error.getMessage().contains("same ploidy"), error::getMessage);
    }

    private static Set<String> stateNodeIds(BEASTState state) {
        return state.stateNodes.keySet().stream().map(StateNode::getID).collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "explicit={0}")
    @ValueSource(booleans = {false, true})
    void fixedStatesMatchLPhyBeastTermByTerm(boolean explicit) throws Exception {
        Properties reference = new Properties();
        try (var in = getClass().getResourceAsStream("/crc09-reference.properties")) {
            reference.load(in);
        }
        for (String name : reference.getProperty("states").split(",")) {
            assertStateMatchesLPhyBeast(explicit, reference, name);
        }
    }

    /**
     * Compares the likelihood and every prior term at one fixed state. LPhyBEAST omits the
     * uniform prior on the group sizes: its mass is -log 4 for the four compositions of five
     * coalescences into two groups, (1,4), (2,3), (3,2) and (4,1).
     */
    private void assertStateMatchesLPhyBeast(boolean explicit, Properties reference, String name) throws Exception {
        BEASTState graph = evaluate(model(explicit).formatted(data()));
        Distribution likelihood = graph.likelihoodDistributions.getFirst();
        assertEquals(1, graph.likelihoodDistributions.size());
        CompoundDistribution prior = new CompoundDistribution();
        prior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, likelihood));
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);

        for (StateNode node : graph.stateNodes.keySet()) {
            String value = reference.getProperty(name + "." + node.getID());
            assertTrue(value != null, "Missing fixed state for " + name + "." + node.getID());
            setReferenceValue(node, value);
        }
        state.robustlyCalcPosterior(posterior);

        Set<String> lphyBeastTerms = reference.stringPropertyNames().stream()
                .filter(key -> key.startsWith(name + ".prior."))
                .map(key -> key.substring((name + ".prior.").length()))
                .collect(Collectors.toSet());
        Set<String> phyloSpecTerms = graph.priorDistributions.keySet().stream()
                .map(StateNode::getID)
                .filter(id -> !id.equals("groupSizes"))
                .collect(Collectors.toSet());
        assertEquals(lphyBeastTerms, phyloSpecTerms, name + ": prior terms");
        assertAll(graph.priorDistributions.entrySet().stream().map(term -> () -> {
            String id = term.getKey().getID();
            double expected = id.equals("groupSizes")
                    ? -Math.log(4.0)
                    : Double.parseDouble(reference.getProperty(name + ".prior." + id));
            assertEquals(expected, term.getValue().getCurrentLogP(), 1e-8, name + ": prior of " + id);
        }));
        assertEquals(Double.parseDouble(reference.getProperty(name + ".likelihood")),
                likelihood.getCurrentLogP(), 1e-8, name + ": read-count tree log likelihood");
    }

    private static void setReferenceValue(StateNode node, String value) {
        if (node instanceof Tree tree) {
            TreeParser fixed = new TreeParser();
            fixed.initByName("taxonset", tree.getTaxonset(), "newick", value,
                    "IsLabelledNewick", true, "adjustTipHeights", false);
            for (int i = 0; i < tree.getLeafNodeCount(); i++) {
                assertEquals(tree.getNode(i).getID(), fixed.getNode(i).getID());
            }
            tree.assignFromWithoutID(fixed);
        } else if (node instanceof RealScalarParam<?> scalar) {
            scalar.set(Double.parseDouble(value));
        } else if (node instanceof RealVectorParam<?> vector) {
            String[] entries = value.split(" ");
            assertEquals(vector.size(), entries.length, node.getID());
            for (int i = 0; i < entries.length; i++) vector.set(i, Double.parseDouble(entries[i]));
        } else {
            var groups = assertInstanceOf(IntVectorParam.class, node);
            String[] entries = value.split(" ");
            assertEquals(groups.size(), entries.length, node.getID());
            for (int i = 0; i < entries.length; i++) groups.set(i, Integer.parseInt(entries[i]));
        }
    }

    @ParameterizedTest(name = "explicit={0}")
    @ValueSource(booleans = {false, true})
    void crc09StructureRunsAsMCMC(boolean explicit) throws Exception {
        Path source = this.directory.resolve("crc09-small.phylospec");
        Files.writeString(source, model(explicit).formatted("six-cells.mpileup") + """

                mcmc {
                    Integer chainLength = 20000
                    Integer defaultLogEvery = 1000
                }
                """);
        data();
        String prefix = this.directory.resolve("analysis").toString();

        new PhyloSpecRunner(source, LIBRARIES).runPhyloSpec(prefix);

        List<String> trace = Files.readAllLines(this.directory.resolve("analysis.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> columns = List.of(trace.getFirst().trim().split("\\s+"));
        assertTrue(columns.contains("likelihood"), columns.toString());
        List<Integer> groups = IntStream.range(0, columns.size())
                .filter(i -> columns.get(i).startsWith("groupSizes"))
                .boxed()
                .toList();
        assertEquals(2, groups.size(), columns.toString());

        double first = Double.NaN;
        double last = Double.NaN;
        for (String row : trace.subList(1, trace.size())) {
            String[] values = row.trim().split("\\s+");
            double likelihood = Double.parseDouble(values[columns.indexOf("likelihood")]);
            assertTrue(Double.isFinite(likelihood), row);
            if (Double.isNaN(first)) first = likelihood;
            last = likelihood;
            int sum = 0;
            for (int column : groups) sum += (int) Double.parseDouble(values[column]);
            assertEquals(5, sum, row);
        }
        assertTrue(last > first, "the likelihood should improve from the default state: " + first + " -> " + last);
    }

    /**
     * BEAST's debug mode recomputes the posterior from scratch after each of the first 6000 steps
     * and compares it with the value computed incrementally, so every operator must trigger the
     * right recalculations. On a difference BEAST stops the JVM, and Maven reports this test class
     * as crashed ("The forked VM terminated without properly saying goodbye").
     */
    @ParameterizedTest(name = "explicit={0}")
    @ValueSource(booleans = {false, true})
    void everyMcmcStepKeepsThePosteriorConsistent(boolean explicit) throws Exception {
        Path source = this.directory.resolve("crc09-debug.phylospec");
        Files.writeString(source, model(explicit).formatted("six-cells.mpileup") + """

                mcmc {
                    Integer chainLength = 6000
                    Integer defaultLogEvery = 1000
                    Integer randomSeed = 1
                }
                """);
        data();
        System.setProperty("beast.debug", "true");
        try {
            new PhyloSpecRunner(source, LIBRARIES).runPhyloSpec(this.directory.resolve("debug").toString());
        } finally {
            System.clearProperty("beast.debug");
        }
    }

    private BEASTState evaluate(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, LIBRARIES);
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState state = evaluator.applyBestTiling(new BEASTState(this.directory.resolve("test").toString(), this.directory));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
