import dr.evomodel.tree.TreeModel;
import dr.inference.model.Likelihood;
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
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiles.BeastXCoreTileLibrary;
import tiling.BeastXState;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Values of BEAST X functions, checked against what the PhyloSpec reference says they should be.
 * Functions of sampled values are also checked to follow changes to those values.
 */
public class BeastXFunctionValuesTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    public void mathFunctionsOfFixedValues() {
        assertEquals(Math.exp(1.5), real("exp(x=1.5)"), TOLERANCE);
        assertEquals(Math.log(2.0), real("log(x=2.0)"), TOLERANCE);
        assertEquals(3.0, real("log(x=8.0, base=2)"), TOLERANCE);
        assertEquals(2.5, real("sqrt(x=6.25)"), TOLERANCE);
    }

    @Test
    public void mathFunctionsFollowSampledValues() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                Real e = exp(x=r)
                Real l = log(x=r)
                Real s = sqrt(x=r)
                """).buildState("test");

        Parameter r = state.stateNodesByPhyloSpecName.get("r");

        for (double value : new double[]{2.0, 0.3}) {
            r.setParameterValue(0, value);

            assertEquals(Math.exp(value), statistic(state, "e"), TOLERANCE);
            assertEquals(Math.log(value), statistic(state, "l"), TOLERANCE);
            assertEquals(Math.sqrt(value), statistic(state, "s"), TOLERANCE);
        }
    }

    @Test
    public void vectorFunctions() {
        assertArrayEquals(new double[]{0.0, 0.25, 0.5, 0.75, 1.0}, reals("linspace(start=0.0, end=1.0, num=5)"), TOLERANCE);
        assertArrayEquals(new double[]{0.25, 0.25, 0.25}, reals("repeat(value=0.25, num=3)"), TOLERANCE);
        // The integer and real repeat tiles both fit an integer literal, so either may be chosen.
        assertArrayEquals(new double[]{2.0, 2.0}, numbers("Vector<Integer> r = repeat(value=2, num=2)\n"), TOLERANCE);
        assertEquals(3, integer("Integer n = num(vector=[1.0, 2.0, 3.0])\n"));
        assertEquals(2, integer("Matrix<Real> m = [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]\nInteger n = numRows(matrix=m)\n"));
        assertEquals(3, integer("Matrix<Real> m = [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]\nInteger n = numCols(matrix=m)\n"));
    }

    @Test
    public void treeAndTaxonAccessors() {
        String tree = "Tree tree = fromNewick(\"((A:1.0,B:1.0):1.0,C:1.5);\")\n";

        assertEquals(3, integer(tree + "Integer n = numTaxa(tree=tree)\n"));
        assertEquals(4, integer(tree + "Integer n = numBranches(tree=tree)\n"));
        assertEquals(0.5, ((RealScalar<?>) lastValue(tree + "Real a = age(node=\"C\", tree=tree)\n")).get(), TOLERANCE);
        assertEquals(2.5, ((RealScalar<?>) lastValue("Taxon t = taxon(name=\"A\", age=2.5)\nReal a = age(taxon=t)\n")).get(), TOLERANCE);
    }

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

    /**
     * Values computed from sampled values (sum, exp, log, sqrt, arithmetic, a computed value of a
     * computed value, and the root age) are logged in memory and from XML, and in every logged row
     * each one equals its formula applied to the inputs logged in the same row.
     */
    @Test
    public void computedValuesAreLoggedInMemoryAndFromXml() throws Exception {
        String model = """
                Vector<Real> v ~ IID(base=Normal(mean=0.0, sd=1.0), num=3)
                PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)
                Taxa taxa = taxa(tree=fromNewick("((A:1.0,B:1.0):1.0,C:2.0);"))
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                Age root = rootAge(tree=tree)
                Real s = sum(vector=v)
                Real k = s / 4.0
                Real m = 2.0 * r + 1.0
                Real e = exp(x=r)
                Real l = log(x=r, base=2)
                Real q = sqrt(x=r)
                Real n = -r
                Real h = rootAge(tree=tree) * 2.0
                Real u = sum(vector=[1.0, 2.0]) + r
                """;

        for (boolean viaXml : new boolean[]{false, true}) {
            String runName = "computedValues" + (viaXml ? "Xml" : "InMemory");
            Path logPath = XmlTestSupport.logPath(runName);
            XmlTestSupport.prepare(logPath);

            PhyloSpecRunner runner = new PhyloSpecRunner(model + """

                    mcmc {
                        Integer chainLength = 20000
                        Integer randomSeed = 7
                        Logger fileLogger = fileLogger(logEvery=100, file="%s", parameters=[v, r, root, s, k, m, e, l, q, n, h, u])
                    }
                    """.formatted(XmlTestSupport.unixPath(logPath)));

            if (viaXml) {
                Path xmlPath = XmlTestSupport.xmlPath(runName);
                XmlTestSupport.prepare(xmlPath);
                runner.executeXmlRun(XmlRunnerOptions.builder(runName, xmlPath).build());
            } else {
                runner.run(RunnerOptions.builder(runName).mode(RunMode.EXECUTE_MCMC).build());
            }

            Map<String, List<Double>> log = new HashMap<>();
            for (String column : List.of("v1", "v2", "v3", "r", "root", "s", "k", "m", "e", "l", "q", "n", "h", "u")) {
                log.put(column, XmlTestSupport.readColumn(logPath, column));
            }

            // The inputs must move, or the check would only see the starting values.
            assertTrue(new HashSet<>(log.get("r")).size() > 10, runName + ": r did not move");

            for (int row = 0; row < log.get("r").size(); row++) {
                double r = log.get("r").get(row);
                double sum = log.get("v1").get(row) + log.get("v2").get(row) + log.get("v3").get(row);
                String at = runName + ", row " + row + ": ";

                assertEquals(sum, log.get("s").get(row), 1e-9, at + "s");
                assertEquals(sum / 4.0, log.get("k").get(row), 1e-9, at + "k");
                assertEquals(2.0 * r + 1.0, log.get("m").get(row), 1e-9, at + "m");
                assertEquals(Math.exp(r), log.get("e").get(row), 1e-9, at + "e");
                assertEquals(Math.log(r) / Math.log(2.0), log.get("l").get(row), 1e-9, at + "l");
                assertEquals(Math.sqrt(r), log.get("q").get(row), 1e-9, at + "q");
                assertEquals(-r, log.get("n").get(row), 1e-9, at + "n");
                assertEquals(2.0 * log.get("root").get(row), log.get("h").get(row), 1e-9, at + "h");
                assertEquals(3.0 + r, log.get("u").get(row), 1e-9, at + "u");
            }
        }
    }

    private static double statistic(BeastXState state, String name) {
        return state.calculationNodesByPhyloSpecName.get(name).getStatisticValue(0);
    }

    /**
     * The value of a positive constant {@code call}. Distribution parameters take plain values, so
     * using it as the upper bound of a Uniform checks the constant tiles, and the Uniform density
     * -log(upper) gives the value back.
     */
    private static double real(String call) {
        try {
            BeastXState state = new PhyloSpecRunner("Real x ~ Uniform(lower=0.0, upper=" + call + ")\n").buildState("test");
            Parameter x = state.stateNodesByPhyloSpecName.get("x");
            x.setParameterValue(0, 0.01);
            Likelihood prior = state.priorDistributions.get(x);
            prior.makeDirty();
            return Math.exp(-prior.getLogLikelihood());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static double[] reals(String call) {
        return ((RealVector<?>) lastValue("Vector<Real> y = " + call + "\n")).getDoubleArray();
    }

    private static double[] numbers(String source) {
        Object value = lastValue(source);
        if (value instanceof IntVector<?> vector) {
            return java.util.Arrays.stream(vector.getIntArray()).asDoubleStream().toArray();
        }
        return ((RealVector<?>) value).getDoubleArray();
    }

    private static int integer(String source) {
        return ((IntScalar<?>) lastValue(source)).get();
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
