package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.MCMC;
import beast.base.inference.State;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.operator.AdaptableVarianceMultivariateNormalOperator;
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
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;

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

    private static BEASTState tile(String source) throws IOException {
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
        BEASTState state = new BEASTState("popfunc-workflow");

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            evaluator.getBestTiling(statements);
            return evaluator.applyBestTiling(state);
        } finally {
            System.setOut(originalOut);
        }
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
