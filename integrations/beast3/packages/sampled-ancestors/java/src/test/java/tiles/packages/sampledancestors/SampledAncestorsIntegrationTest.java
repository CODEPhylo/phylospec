package tiles.packages.sampledancestors;

import beast.base.evolution.tree.Tree;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import sa.evolution.operators.LeafToSampledAncestorJump;
import sa.evolution.operators.SAExchange;
import sa.evolution.operators.SAScaleOperator;
import sa.evolution.operators.SAUniform;
import sa.evolution.operators.SAWilsonBalding;
import sa.evolution.speciation.SABirthDeathModel;
import tiles.BeastTileLibraries;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampledAncestorsIntegrationTest {

    @Test
    public void discoversFossilizedBirthDeathTileThroughServiceLoader() {
        assertTrue(
                BeastTileLibraries.loadAll().stream()
                        .anyMatch(FossilizedBirthDeathTile.class::isInstance));
    }

    @Test
    public void buildsDirectRateFossilizedBirthDeathWithoutRootAge() throws Exception {
        BEASTState beastState =
                buildState(
                        """
                        Alignment data = fromNexus("src/test/java/resources/dated-simple.nex")
                        Tree tree ~ FossilizedBirthDeath(
                            speciationRate=1.0,
                            extinctionRate=0.2,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            taxa=taxa(data)
                        )
                        """);

        assertFossilizedBirthDeathState(beastState);
    }

    @Test
    public void buildsDiversificationTurnoverFossilizedBirthDeath() throws Exception {
        BEASTState beastState =
                buildState(
                        """
                        Alignment data = fromNexus("src/test/java/resources/dated-simple.nex")
                        Tree tree ~ FossilizedBirthDeath(
                            diversificationRate=0.8,
                            turnover=0.2,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            rootAge=5.0,
                            taxa=taxa(data)
                        )
                        """);

        Tree tree = assertFossilizedBirthDeathState(beastState);
        SABirthDeathModel model =
                assertInstanceOf(SABirthDeathModel.class, beastState.priorDistributions.get(tree));

        assertEquals(1.0, model.birthRateInput.get().get(), 1.0e-12);
        assertEquals(0.2, model.deathRateInput.get().get(), 1.0e-12);
    }

    @Test
    public void preservesDatedTipsInFossilizedBirthDeathTree() throws Exception {
        BEASTState beastState =
                buildState(
                        """
                        Alignment data = fromNexus(
                            file="src/test/java/resources/dated-simple.nex",
                            age=parse(regex=".*_(\\d+(?:\\.\\d+)?)$")
                        )
                        Tree tree ~ FossilizedBirthDeath(
                            speciationRate=1.0,
                            extinctionRate=0.2,
                            serialSamplingRate=0.1,
                            samplingProbability=0.8,
                            rootAge=5.0,
                            taxa=taxa(data)
                        )
                        """);

        Tree tree = assertFossilizedBirthDeathState(beastState);
        double oldestTipAge =
                tree.getExternalNodes().stream()
                        .mapToDouble(node -> node.getHeight())
                        .max()
                        .orElseThrow();

        assertEquals(3.0, oldestTipAge, 1.0e-12);
    }

    private static BEASTState buildState(String source) throws Exception {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        statements = new RemoveGroupings().transform(statements);
        statements = new EvaluateLiterals().transform(statements);
        statements = new EvaluateScalarFunctions().transform(statements);

        VariableResolver variableResolver = new VariableResolver(statements);
        TypeResolver typeResolver =
                new TypeResolver(
                        new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        typeResolver.visitStatements(statements);

        StochasticityResolver stochasticityResolver = new StochasticityResolver();
        stochasticityResolver.visitStatements(statements);

        EvaluateTiles<BEASTState> evaluateTiles =
                new EvaluateTiles<>(
                        BeastTileLibraries.loadAll(),
                        new ArrayList<>(),
                        variableResolver,
                        stochasticityResolver);
        evaluateTiles.getBestTiling(statements);

        return evaluateTiles.applyBestTiling(new BEASTState("sampled-ancestors-test"));
    }

    private static Tree assertFossilizedBirthDeathState(BEASTState beastState) {
        assertEquals(
                1,
                beastState.priorDistributions.values().stream()
                        .filter(SABirthDeathModel.class::isInstance)
                        .count());

        for (StateNode stateNode : beastState.stateNodes.keySet()) {
            if (!beastState.addPackageOperators(stateNode)) {
                OperatorSelector.addDefaultOperators(stateNode, beastState);
            }
        }

        Tree tree =
                beastState.stateNodes.keySet().stream()
                        .filter(Tree.class::isInstance)
                        .map(Tree.class::cast)
                        .findFirst()
                        .orElseThrow();

        List<Operator> treeOperators =
                beastState.operators.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(tree))
                        .map(Map.Entry::getKey)
                        .toList();

        assertEquals(7, treeOperators.size());
        assertOperatorCount(treeOperators, LeafToSampledAncestorJump.class, 1);
        assertOperatorCount(treeOperators, SAWilsonBalding.class, 1);
        assertOperatorCount(treeOperators, SAExchange.class, 2);
        assertOperatorCount(treeOperators, SAUniform.class, 1);
        assertOperatorCount(treeOperators, SAScaleOperator.class, 2);

        assertInstanceOf(SABirthDeathModel.class, beastState.priorDistributions.get(tree));
        assertDoesNotThrow(beastState::initializeBEASTObjects);
        return tree;
    }

    private static void assertOperatorCount(
            List<Operator> operators,
            Class<? extends Operator> operatorClass,
            long expectedCount) {
        assertEquals(
                expectedCount,
                operators.stream().filter(operatorClass::isInstance).count());
    }
}
