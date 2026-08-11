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

public class SampledAncestorsIntegrationTest {

    @Test
    public void buildsAndInitializesDirectRateFossilizedBirthDeath() throws Exception {
        String source =
                """
                Age rootAge ~ Gamma(rate=1.0, shape=2.0)
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Tree tree ~ FossilizedBirthDeath(
                    speciationRate=1.0,
                    extinctionRate=0.2,
                    serialSamplingRate=0.1,
                    samplingProbability=0.8,
                    rootAge=rootAge,
                    taxa=taxa(data)
                )
                """;

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

        BEASTState beastState =
                evaluateTiles.applyBestTiling(new BEASTState("sampled-ancestors-test"));

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

        assertInstanceOf(
                SABirthDeathModel.class,
                beastState.priorDistributions.get(tree));
        assertDoesNotThrow(beastState::initializeBEASTObjects);
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
