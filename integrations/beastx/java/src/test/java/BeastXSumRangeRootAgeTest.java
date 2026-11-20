import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.junit.jupiter.api.Test;
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
import org.phylospec.types.IntVector;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Values of {@code range}, {@code sum} and {@code rootAge}, checked against what the
 * PhyloSpec reference says they should be.
 */
public class BeastXSumRangeRootAgeTest {

    @Test
    public void rangeIncludesBothEnds() {
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, range("range(start=1, end=5)"));
        assertArrayEquals(new int[]{5}, range("range(start=5, end=5)"));
        assertArrayEquals(new int[]{3, 2, 1}, range("range(start=3, end=1)"));
    }

    @Test
    public void sumOfSampledVectorAddsAllElementsAndFollowsChanges() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Vector<Real> v ~ IID(base=Normal(mean=0.0, sd=1.0), num=3)
                Real s = sum(vector=v)
                """).buildState("test");

        Parameter v = state.stateNodesByPhyloSpecName.get("v");
        Statistic s = state.calculationNodesByPhyloSpecName.get("s");

        v.setParameterValue(0, 2.0);
        v.setParameterValue(1, 3.0);
        v.setParameterValue(2, 5.0);
        assertEquals(10.0, s.getStatisticValue(0), 1e-12);

        v.setParameterValue(2, -1.0);
        assertEquals(4.0, s.getStatisticValue(0), 1e-12);
    }

    @Test
    public void sumOfIntegerRange() {
        Object result = lastValue("Integer total = sum(vector=range(start=1, end=4))\n");

        assertEquals(10, ((IntScalar<?>) result).get());
    }

    @Test
    public void rootAgeFollowsTheTree() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=2.0, taxa=taxa)
                Age root = rootAge(tree=tree)
                """).buildState("test");

        TreeModel tree = state.treeModelsByPhyloSpecName.get("tree");
        Statistic rootAge = state.calculationNodesByPhyloSpecName.get("rootAge");

        double height = tree.getNodeHeight(tree.getRoot());
        assertEquals(height, rootAge.getStatisticValue(0), 1e-12);

        tree.setNodeHeight(tree.getRoot(), 2.0 * height);
        assertEquals(2.0 * height, rootAge.getStatisticValue(0), 1e-12);
    }

    private static int[] range(String call) {
        return ((IntVector<?>) lastValue("Vector<Integer> r = " + call + "\n")).getIntArray();
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
