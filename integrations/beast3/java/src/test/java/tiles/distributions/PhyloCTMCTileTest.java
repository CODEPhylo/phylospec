package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;

class PhyloCTMCTileTest {
    @TempDir
    Path directory;

    private String previousJavaOnly;

    @BeforeEach
    void useJavaLikelihood() {
        previousJavaOnly = System.getProperty("java.only");
        System.setProperty("java.only", "true");
    }

    @AfterEach
    void restoreLikelihoodSetting() {
        if (previousJavaOnly == null) {
            System.clearProperty("java.only");
        } else {
            System.setProperty("java.only", previousJavaOnly);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bindsOptionAndMatchesNativeLikelihood(boolean useAmbiguities) throws Exception {
        TreeLikelihood actual = likelihood("R", ", useAmbiguities=" + useAmbiguities);
        assertEquals(useAmbiguities, actual.m_useAmbiguities.get());

        TreeLikelihood expected = new TreeLikelihood();
        expected.initByName(
                "data", actual.dataInput.get(),
                "tree", actual.treeInput.get(),
                "siteModel", actual.siteModelInput.get(),
                "branchRateModel", actual.branchRateModelInput.get(),
                "useAmbiguities", useAmbiguities);

        double logP = actual.calculateLogP();
        assertTrue(Double.isFinite(logP));
        assertEquals(expected.calculateLogP(), logP, 1e-12);
    }

    @Test
    void omissionPreservesFalseDefault() throws Exception {
        TreeLikelihood omitted = likelihood("R", "");
        assertFalse(omitted.m_useAmbiguities.get());
        assertEquals(likelihood("R", ", useAmbiguities=false").calculateLogP(), omitted.calculateLogP(), 1e-12);
    }

    @Test
    void ambiguitySumsOnlyAllowedStatesRatherThanDiscardingTheSite() throws Exception {
        // R denotes A or G at one tip. Other tips and the site remain informative in both modes.
        double probabilityA = Math.exp(likelihood("A", "").calculateLogP());
        double probabilityG = Math.exp(likelihood("G", "").calculateLogP());
        double probabilityC = Math.exp(likelihood("C", "").calculateLogP());
        double probabilityT = Math.exp(likelihood("T", "").calculateLogP());
        double included = likelihood("R", ", useAmbiguities=true").calculateLogP();
        double unknown = likelihood("R", ", useAmbiguities=false").calculateLogP();

        assertEquals(Math.log(probabilityA + probabilityG), included, 1e-12);
        assertEquals(Math.log(probabilityA + probabilityG + probabilityC + probabilityT), unknown, 1e-12);
        assertNotEquals(included, unknown, 1e-8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "N", "?", "-"})
    void unambiguousAndFullyUnknownStatesAreUnaffected(String state) throws Exception {
        assertEquals(
                likelihood(state, ", useAmbiguities=false").calculateLogP(),
                likelihood(state, ", useAmbiguities=true").calculateLogP(),
                1e-12);
    }

    @Test
    void acceptsNamedConstantWithoutAddingStateOrPrior() throws Exception {
        String source = "Boolean ambiguities = true\n" + source("R", ", useAmbiguities=ambiguities");
        BEASTState state = evaluate(source);
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertEquals(1, state.likelihoodDistributions.size());
        assertTrue(((TreeLikelihood) state.likelihoodDistributions.getFirst()).m_useAmbiguities.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "\"true\"", "[true,false]"})
    void rejectsNonBooleanArguments(String value) throws Exception {
        String source = source("R", ", useAmbiguities=" + value);
        assertThrows(TypeError.class, () -> evaluate(source));
    }

    @Test
    void rejectsSampledBooleanAtTheInputBoundary() {
        var statements = new Parser(
                        new Lexer("Boolean sampled ~ Bernoulli(p=0.5)\nBoolean option = sampled").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new PhyloCTMCTile()
                .useAmbiguitiesInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("useAmbiguities"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void advertisesOptionalConstantOnlySupport() throws Exception {
        var input = new PhyloCTMCTile().useAmbiguitiesInput;
        assertFalse(input.isRequired());
        assertEquals(Set.of(Stochasticity.CONSTANT), input.getAcceptedStochasticities());

        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var argument = catalog.getEngineSpecifications().stream()
                .flatMap(engine -> engine.getGenerators().stream())
                .filter(generator -> "PhyloCTMC".equals(generator.getName()))
                .flatMap(generator -> generator.getArguments().stream())
                .filter(candidate -> "useAmbiguities".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        assertFalse(argument.getRequired());
        assertFalse(argument.getCanBeStochastic());
    }

    @Test
    void runsShortObservedAnalysis() throws Exception {
        String source = source("R", ", useAmbiguities=true")
                        .replace(
                                "Tree tree = fromNewick(\"((A:0.1,B:0.1):0.2,(C:0.2,D:0.2):0.1);\")",
                                "Tree tree ~ Yule(birthRate=1.0, taxa=taxa(data))")
                + """

                mcmc {
                  Integer chainLength=20
                  Integer defaultLogEvery=10
                  Integer randomSeed=12345
                }
                """;
        Path model = directory.resolve("analysis.phylospec");
        Files.writeString(model, source);
        new PhyloSpecRunner(model, List.of("beast2"))
                .runPhyloSpec(directory.resolve("analysis").toString());
        List<String> samples = Files.readAllLines(directory.resolve("analysis.log")).stream()
                .filter(line -> line.matches("[0-9]+\\s+.*"))
                .toList();
        assertEquals(3, samples.size());
        assertEquals("20", samples.getLast().split("\\s+")[0]);
        for (String sample : samples) {
            String[] fields = sample.split("\\s+");
            for (int i = 1; i < fields.length; i++) {
                assertTrue(Double.isFinite(Double.parseDouble(fields[i])), sample);
            }
        }
    }

    private TreeLikelihood likelihood(String observedState, String option) throws Exception {
        return (TreeLikelihood)
                evaluate(source(observedState, option)).likelihoodDistributions.getFirst();
    }

    private String source(String observedState, String option) throws Exception {
        Files.writeString(directory.resolve("data.nex"), """
                #NEXUS
                Begin data;
                Dimensions ntax=4 nchar=1;
                Format datatype=dna missing=? gap=-;
                Matrix
                A %s
                B A
                C G
                D T
                ;
                End;
                """.formatted(observedState));
        return """
                Alignment data = fromNexus(file="data.nex")
                Tree tree = fromNewick("((A:0.1,B:0.1):0.2,(C:0.2,D:0.2):0.1);")
                QMatrix q = jc69()
                Alignment alignment ~ PhyloCTMC(tree=tree,qMatrix=q%s) observed as data
                """.formatted(option);
    }

    private BEASTState evaluate(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var tiles = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        tiles.getBestTiling(statements);
        BEASTState state =
                tiles.applyBestTiling(new BEASTState(directory.resolve("test").toString(), directory));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }
}
