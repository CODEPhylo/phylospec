package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.MCMC;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.operator.AdaptableVarianceMultivariateNormalOperator;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;
import runner.PhyloSpecCli;
import runner.PhyloSpecRunner;
import operators.popfunc.ModelIndicatorOperator;

public class PopFuncWorkflowTest {

    @Test
    public void buildsCompleteGompertzAnalysis() throws Exception {
        Path alignment = Path.of("../java/src/test/java/resources/primate-mtDNA.nex")
                .toAbsolutePath()
                .normalize();
        assertTrue(Files.isRegularFile(alignment));

        String source = """
                use popfunc.functions.coalescent

                Alignment data = fromNexus("%s")

                PositiveReal initialPopulationSize ~ LogNormal(logMean=5.0, logSd=0.5)
                PositiveReal growthRate ~ LogNormal(logMean=-0.95, logSd=0.2)
                Probability initialProportion ~ Beta(alpha=20.0, beta=7.0)
                PositiveReal ancestralPopulationSize ~ LogNormal(logMean=3.0, logSd=0.3)

                PopulationFunction population = gompertzF0PopulationFunction(
                    initialProportion=initialProportion,
                    growthRate=growthRate,
                    initialPopulationSize=initialPopulationSize,
                    ancestralPopulationSize=ancestralPopulationSize
                )

                Tree tree ~ Coalescent(
                    populationSize=population,
                    taxa=taxa(data)
                )

                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix
                ) observed as data

                mcmc {
                    Integer chainLength = 10
                }
                """.formatted(alignment.toString());

        BEASTState state = tile(source);
        TileLibrary.configureState(selectedLibraries(), state);

        assertEquals(5, state.stateNodes.size());
        assertEquals(5, state.priorDistributions.size());
        assertEquals(1, state.likelihoodDistributions.size());
        assertEquals(10, state.chainLength);

        Coalescent coalescent = state.priorDistributions.values().stream()
                .filter(Coalescent.class::isInstance)
                .map(Coalescent.class::cast)
                .findFirst()
                .orElseThrow();
        GompertzGrowth_f0 population =
                assertInstanceOf(GompertzGrowth_f0.class, coalescent.popSizeInput.get());

        RealScalar<?> initialProportion = state.stateNodes.keySet().stream()
                .filter(node -> "initialProportion".equals(node.getID()))
                .map(RealScalar.class::cast)
                .findFirst()
                .orElseThrow();
        assertSame(initialProportion, population.f0Input.get());
        assertSame(UnitInterval.INSTANCE, initialProportion.getDomain());

        assertEquals(2, state.operators.stream().filter(UpDownOperator.class::isInstance).count());
        assertEquals(
                1,
                state.operators.stream()
                        .filter(AdaptableVarianceMultivariateNormalOperator.class::isInstance)
                        .count());

        MCMC mcmc = assembleMcmc(state);
        state.initializeBEASTObjects();

        assertEquals(10, mcmc.chainLengthInput.get());
        assertEquals(state.operators, mcmc.operatorsInput.get());
        assertEquals(1, ((CompoundDistribution) mcmc.posteriorInput.get())
                .pDistributions.get()
                .stream()
                .filter(CompoundDistribution.class::isInstance)
                .map(CompoundDistribution.class::cast)
                .filter(distribution -> "likelihood".equals(distribution.getID()))
                .findFirst()
                .orElseThrow()
                .pDistributions
                .get()
                .size());
    }

    @Test
    public void buildsCompleteModelSelectionAnalysis() throws Exception {
        Path alignment = Path.of("../java/src/test/java/resources/primate-mtDNA.nex")
                .toAbsolutePath()
                .normalize();
        assertTrue(Files.isRegularFile(alignment));

        String source = modelSelectionSource(alignment, 10);

        BEASTState state = tile(source);
        TileLibrary.configureState(selectedLibraries(), state);

        assertEquals(8, state.stateNodes.size());
        assertEquals(8, state.priorDistributions.size());
        assertEquals(1, state.likelihoodDistributions.size());

        Coalescent coalescent = state.priorDistributions.values().stream()
                .filter(Coalescent.class::isInstance)
                .map(Coalescent.class::cast)
                .findFirst()
                .orElseThrow();
        StochasticVariableSelection selection =
                assertInstanceOf(StochasticVariableSelection.class, coalescent.popSizeInput.get());
        assertEquals(2, selection.modelsInput.get().size());
        assertInstanceOf(GompertzGrowth_f0.class, selection.modelsInput.get().get(0));
        assertInstanceOf(GompertzGrowth_t50.class, selection.modelsInput.get().get(1));

        IntScalarParam<NonNegativeInt> modelIndex = state.stateNodes.keySet().stream()
                .filter(node -> "modelIndex".equals(node.getID()))
                .map(node -> assertInstanceOf(IntScalarParam.class, node))
                .map(node -> (IntScalarParam<NonNegativeInt>) node)
                .findFirst()
                .orElseThrow();
        assertSame(modelIndex, selection.indicatorInput.get());
        IntUniform modelPrior =
                assertInstanceOf(IntUniform.class, state.priorDistributions.get(modelIndex));
        assertEquals(0, modelPrior.getLowerBoundOfParameter());
        assertEquals(1, modelPrior.getUpperBoundOfParameter());

        ModelIndicatorOperator indicatorOperator = state.operators.stream()
                .filter(ModelIndicatorOperator.class::isInstance)
                .map(ModelIndicatorOperator.class::cast)
                .findFirst()
                .orElseThrow();
        assertSame(modelIndex, indicatorOperator.indicatorInput.get());
        assertEquals(3, state.operators.stream().filter(UpDownOperator.class::isInstance).count());
        assertEquals(
                2,
                state.operators.stream()
                        .filter(AdaptableVarianceMultivariateNormalOperator.class::isInstance)
                        .count());

        MCMC mcmc = assembleMcmc(state);
        state.initializeBEASTObjects();
        assertEquals(state.operators, mcmc.operatorsInput.get());
    }

    @Test
    public void runsCompleteModelSelectionMcmc(@TempDir Path outputDirectory) throws Exception {
        Path alignment = Path.of("../java/src/test/java/resources/primate-mtDNA.nex")
                .toAbsolutePath()
                .normalize();
        Path traceFile = outputDirectory.resolve("popfunc.log");
        Path treeFile = outputDirectory.resolve("popfunc.trees");

        BEASTState state = tile(modelSelectionSource(alignment, 25), "popfunc-mcmc");
        TileLibrary.configureState(selectedLibraries(), state);
        state.addFileLoggerSpec(new FileLoggerSpec<>(1, traceFile.toString(), null));
        state.addTreeLoggerSpec(new TreeLoggerSpec<>(1, treeFile.toString(), null));

        ModelIndicatorOperator indicatorOperator = state.operators.stream()
                .filter(ModelIndicatorOperator.class::isInstance)
                .map(ModelIndicatorOperator.class::cast)
                .findFirst()
                .orElseThrow();
        state.setInput(indicatorOperator, indicatorOperator.m_pWeight, 1000.0);

        MCMC mcmc = assembleMcmc(state);
        mcmc.setStateFile(outputDirectory.resolve("popfunc.state.xml").toString(), false);
        state.initializeBEASTObjects();
        mcmc.run();

        int indicatorProposals = indicatorOperator.get_m_nNrAccepted()
                + indicatorOperator.get_m_nNrRejected();
        assertTrue(indicatorProposals > 0);
        assertTrue(Double.isFinite(mcmc.posteriorInput.get().getCurrentLogP()));
        assertTrue(Files.isRegularFile(traceFile));
        assertTrue(Files.size(traceFile) > 0);
        assertTrue(Files.isRegularFile(treeFile));
        assertTrue(Files.size(treeFile) > 0);
    }

    @Test
    public void runsModelSelectionThroughPhyloSpecRunner(@TempDir Path outputDirectory)
            throws Exception {
        Path alignment = Path.of("../java/src/test/java/resources/primate-mtDNA.nex")
                .toAbsolutePath()
                .normalize();
        Path sourceFile = outputDirectory.resolve("analysis.phylospec");
        Files.writeString(sourceFile, modelSelectionSource(alignment, 10));

        String runName = outputDirectory.resolve("popfunc-runner").toString();
        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourceFile), List.of("popfunc", "beast2"));
        runner.runPhyloSpec(runName);

        Path traceFile = Path.of(runName + ".log");
        Path treeFile = Path.of(runName + ".trees");
        Path stateFile = Path.of(runName + ".state.xml");

        assertTrue(Files.readString(traceFile).contains("posterior"));
        assertTrue(Files.readString(treeFile).contains("#NEXUS"));
        assertTrue(Files.isRegularFile(stateFile));
        assertTrue(Files.size(stateFile) > 0);
    }

    @Test
    public void runsFixedGompertzThroughCommandLine(@TempDir Path outputDirectory)
            throws Exception {
        Path alignment = Path.of("../java/src/test/java/resources/primate-mtDNA.nex")
                .toAbsolutePath()
                .normalize();
        Files.copy(alignment, outputDirectory.resolve("data.nex"));
        String outputPrefix = outputDirectory.resolve("script-output").toString();
        String source = """
                use popfunc.functions.coalescent

                Alignment data = fromNexus(file="data.nex")

                PopulationFunction population = gompertzF0PopulationFunction(
                    initialProportion=0.2,
                    growthRate=0.3,
                    initialPopulationSize=1000.0,
                    ancestralPopulationSize=100.0
                )
                Tree tree ~ Coalescent(populationSize=population, taxa=taxa(data))
                QMatrix qMatrix = jc69()
                Vector<Rate> branchRates ~ StrictClock(clockRate=1.0, tree=tree)
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix,
                    branchRates=branchRates
                ) observed as data

                mcmc {
                    Integer chainLength = 10
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                    Integer randomSeed = 12345
                }
                """.formatted(outputPrefix);

        Path sourceFile = outputDirectory.resolve("fixed-gompertz.phylospec");
        Files.writeString(sourceFile, source);
        String runName = outputDirectory.resolve("fixed-gompertz").toString();
        PhyloSpecCli.execute(new String[] {
            "--library", "popfunc,beast2", "--run-name", runName, sourceFile.toString()
        });

        Path traceFile = Path.of(outputPrefix + ".log");
        assertTrue(Files.isRegularFile(traceFile));
        assertEquals(
                11,
                Files.readAllLines(traceFile).stream()
                        .filter(line -> line.strip().matches("\\d+\\s+.*"))
                        .count());
        assertTrue(Files.isRegularFile(Path.of(outputPrefix + ".trees")));
        assertTrue(Files.isRegularFile(Path.of(outputPrefix + ".state.xml")));
    }

    private static BEASTState tile(String source) throws IOException {
        return tile(source, "popfunc-workflow");
    }

    private static BEASTState tile(String source, String runName) throws IOException {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);
        statements = new EvaluateScalarFunctions().transform(statements);

        List<TileLibrary<BEASTState>> libraries = selectedLibraries();
        ComponentResolver resolver =
                new ComponentResolver(TileLibrary.loadComponentLibraries(libraries));
        new TypeResolver(resolver).visitStatements(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluator = new EvaluateTiles<>(
                TileLibrary.combine(libraries), variableResolver, stochasticityResolver);
        BEASTState state = new BEASTState(runName);

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            evaluator.getBestTiling(statements);
            return evaluator.applyBestTiling(state);
        } finally {
            System.setOut(originalOut);
        }
    }

    private static String modelSelectionSource(Path alignment, int chainLength) {
        return """
                use popfunc.functions.coalescent
                use popfunc.distributions

                Alignment data = fromNexus("%s")

                PositiveReal f0PopulationSize ~ LogNormal(logMean=5.0, logSd=0.5)
                PositiveReal f0GrowthRate ~ LogNormal(logMean=-0.95, logSd=0.2)
                Probability initialProportion ~ Beta(alpha=20.0, beta=7.0)
                PopulationFunction f0Model = gompertzF0PopulationFunction(
                    initialProportion=initialProportion,
                    growthRate=f0GrowthRate,
                    initialPopulationSize=f0PopulationSize
                )

                Age halfCapacityAge ~ Exponential(rate=0.2)
                PositiveReal t50GrowthRate ~ LogNormal(logMean=-0.95, logSd=0.2)
                PositiveReal carryingCapacity ~ LogNormal(logMean=5.0, logSd=0.5)
                PopulationFunction t50Model = gompertzT50PopulationFunction(
                    halfCapacityAge=halfCapacityAge,
                    growthRate=t50GrowthRate,
                    carryingCapacity=carryingCapacity
                )

                Vector<PopulationFunction> models = [f0Model, t50Model]
                NonNegativeInteger modelIndex ~ modelIndicator(models=models)
                PopulationFunction population = stochasticPopulationSelection(
                    indicator=modelIndex,
                    models=models
                )

                Tree tree ~ Coalescent(
                    populationSize=population,
                    taxa=taxa(data)
                )

                QMatrix qMatrix = jc69()
                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix
                ) observed as data

                mcmc {
                    Integer chainLength = %d
                }
                """.formatted(alignment.toString(), chainLength);
    }

    private static MCMC assembleMcmc(BEASTState state) {
        State beastState = new State();
        state.setInput(beastState, beastState.stateNodeInput, new ArrayList<>(state.stateNodes.keySet()));

        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(state.getAvailableID("prior"));
        state.setInput(prior, prior.pDistributions, new ArrayList<>(state.priorDistributions.values()));

        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(state.getAvailableID("likelihood"));
        state.setInput(likelihood, likelihood.pDistributions, state.likelihoodDistributions);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(state.getAvailableID("posterior"));
        state.setInput(posterior, posterior.pDistributions, List.of(prior, likelihood));

        MCMC mcmc = new MCMC();
        state.setInput(mcmc, mcmc.chainLengthInput, state.chainLength);
        state.setInput(mcmc, mcmc.startStateInput, beastState);
        state.setInput(mcmc, mcmc.posteriorInput, posterior);
        state.setInput(mcmc, mcmc.operatorsInput, state.operators);
        state.setInput(mcmc, mcmc.loggersInput, state.buildLoggers(posterior, prior, likelihood));
        return mcmc;
    }

    private static List<TileLibrary<BEASTState>> selectedLibraries() {
        return TileLibrary.select(BEASTState.class, List.of("popfunc", "beast2"));
    }
}
