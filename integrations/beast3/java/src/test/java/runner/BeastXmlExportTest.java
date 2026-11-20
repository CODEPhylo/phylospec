package runner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.inference.MCMC;
import beast.base.parser.XMLParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exported analyses must load again in BEAST. Each model uses some of the integration's own BEAST
 * classes, which BEAST can only create from XML when they are registered in {@code version.xml}.
 */
class BeastXmlExportTest {

    private static final String TAXA = "Taxa samples = [taxon(name=\"a\"),taxon(name=\"b\"),taxon(name=\"c\"),"
            + "taxon(name=\"d\"),taxon(name=\"e\"),taxon(name=\"f\")]\n";

    @TempDir
    Path directory;

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(
                        "discrete distributions",
                        """
                        Probability p ~ Beta(alpha=2.0, beta=2.0)
                        Boolean b ~ Bernoulli(p=p)
                        Count g ~ Geometric(p=p)
                        Count n ~ Binomial(numTrials=10, p=p)
                        PositiveInteger c ~ Categorical(probabilities=[0.2, 0.3, 0.5])
                        """,
                        List.of(
                                "tiling.BernoulliDistribution",
                                "tiling.GeometricDistribution",
                                "tiling.BinomialDistribution",
                                "tiling.CategoricalDistribution",
                                "operators.IntScalarUniformOperator")),
                Arguments.of(
                        "continuous distributions",
                        """
                        Simplex weights ~ Dirichlet(concentration=[1.0, 1.0])
                        Real m ~ Mixture(components=[Normal(mean=-1.0, sd=1.0), Normal(mean=2.0, sd=0.5)], weights=weights)
                        Real h ~ Normal(mean=0.0, sd=1.0)
                        Real t ~ Truncated(base=Normal(mean=h, sd=1.0), lower=-10.0, upper=10.0)
                        Real o ~ Offset(base=Normal(mean=0.0, sd=1.0), offset=h)
                        Vector<PositiveReal> chain ~ ExponentialMarkovChain(initialMean=1.0, numValues=3)
                        """,
                        List.of(
                                "tiling.LiveDirichletDistribution",
                                "tiling.NormalMixtureDistribution",
                                "tiling.LiveTruncatedDistribution",
                                "tiling.LiveOffsetDistribution",
                                "tiling.LiveExponentialMarkovChainDistribution")),
                Arguments.of(
                        "functions of random variables",
                        """
                        Real a ~ Normal(mean=0.0, sd=1.0)
                        PositiveReal e = exp(a)
                        Real l = log(e)
                        NonNegativeReal r = sqrt(e)
                        Vector<Real> v ~ IID(base=Normal(mean=0.0, sd=1.0), num=3)
                        Real s = sum(vector=v)
                        Real y ~ Normal(mean=s, sd=e)
                        Real q ~ Normal(mean=r, sd=1.0)
                        Real w ~ Normal(mean=l, sd=1.0)
                        """,
                        List.of("tiling.ExpValue", "tiling.LogValue", "tiling.SqrtValue", "tiling.RealVectorSum")),
                Arguments.of(
                        "birth-death with birth and death rates",
                        TAXA
                                + "Tree tree ~ BirthDeath(birthRate=2.0, deathRate=0.5, samplingProbability=0.8, taxa=samples)\n",
                        List.of("tiling.LiveBirthDeathModel", "tiling.BirthDeathRateValue")),
                Arguments.of(
                        "root age of a sampled tree",
                        TAXA + "Tree tree ~ Yule(birthRate=2.0, taxa=samples)\n"
                                + "Age root = rootAge(tree=tree)\n"
                                + "Real z ~ Normal(mean=root, sd=1.0)\n",
                        List.of("tiling.LiveYuleModel", "tiling.RootAgeValue")),
                Arguments.of(
                        "Bayesian skyline with random group sizes", TAXA + """
                        Vector<PositiveInteger> groupSizes ~ RandomComposition(total=5, numParts=2)
                        PositiveReal initialMean ~ LogNormal(logMean=0.0, logSd=1.0)
                        Vector<PositiveReal> populationSizes ~ ExponentialMarkovChain(initialMean, numValues=2)
                        Tree tree ~ SkylineCoalescent(populationSizes, groupSizes, taxa=samples)
                        """, List.of("tiling.UniformComposition")),
                Arguments.of(
                        "skyline with change times",
                        TAXA + """
                        Vector<PositiveReal> sizes ~ IID(base=LogNormal(logMean=0.0, logSd=1.0), num=2)
                        Tree tree ~ SkylineCoalescent(populationSizes=sizes, changeTimes=[1.0], taxa=samples)
                        """,
                        List.of("tiling.SkylineCoalescentModel", "tiling.PiecewiseConstantPopulation")),
                Arguments.of(
                        "element of a sampled simplex",
                        """
                        Simplex pi ~ Dirichlet(concentration=[1.0, 1.0, 1.0])
                        Real x[i] ~ Normal(mean=pi[i], sd=1.0) for i in 1:3
                        """,
                        List.of("beast.base.spec.inference.parameter.VectorElement")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("models")
    void exportedModelLoadsInBeast(String name, String source, List<String> expectedClasses) throws Exception {
        Path xml = directory.resolve("analysis.xml");

        new PhyloSpecRunner(source).exportXml(xml);

        String content = Files.readString(xml);
        for (String expectedClass : expectedClasses) {
            assertTrue(content.contains("spec=\"" + expectedClass + "\""), name + " should use " + expectedClass);
        }
        assertInstanceOf(MCMC.class, new XMLParser().parseFile(xml.toFile()));
    }

    @Test
    void bundledHkyYuleExampleLoadsInBeast() throws Exception {
        Path xml = directory.resolve("hky-yule.xml");

        new PhyloSpecRunner(Path.of("src/main/distribution/examples/hky-yule.phylospec"), List.of("beast2"))
                .exportXml(xml);

        String content = Files.readString(xml);
        assertTrue(content.contains("spec=\"tiling.LiveYuleModel\""));
        assertTrue(content.contains("spec=\"tiling.LiveDirichletDistribution\""));
        assertInstanceOf(MCMC.class, new XMLParser().parseFile(xml.toFile()));
    }
}
