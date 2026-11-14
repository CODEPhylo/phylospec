import dr.evomodel.substmodel.BaseSubstitutionModel;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.aminoacid.JTT;
import dr.evomodel.substmodel.aminoacid.LG;
import dr.evomodel.substmodel.aminoacid.WAG;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that sampled base frequencies of the amino-acid (JTT, WAG, LG) and codon (GY94)
 * substitution models stay live, in memory and after an XML round trip through BEAST X.
 */
public class BeastXLiveFrequencyParametersTest {

    private static final double TOLERANCE = 1e-8;

    private static final String PROTEIN_DATA = """
            Alignment data = fromNexus("src/test/java/resources/protein-simple.nex")
            Tree tree = fromNewick("((taxon1:0.1,taxon2:0.2):0.05,(taxon3:0.15,taxon4:0.1):0.05);")
            """;

    static Stream<Arguments> substitutionModels() {
        return Stream.of(
                Arguments.of("jtt", "jtt(baseFrequencies=pi)", 20),
                Arguments.of("wag", "wag(baseFrequencies=pi)", 20),
                Arguments.of("lg", "lg(baseFrequencies=pi)", 20),
                Arguments.of("gy94", "gy94(kappa=2.0, omega=0.5, baseFrequencies=pi)", 61)
        );
    }

    /**
     * The substitution model must hold the sampled frequency parameter itself, so a proposal
     * on {@code pi} changes the model's stationary frequencies.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("substitutionModels")
    public void substitutionModelReferencesSampledFrequencies(String name, String qMatrix, int size) {
        String source = "Simplex pi ~ Dirichlet(concentration=repeat(value=1.0, num=" + size + "))\n"
                + "QMatrix q = " + qMatrix + "\n";

        BeastXState state = new BeastXState("liveFrequencies");
        List<Object> values = applyTiles(source, state);
        FrequencyModel frequencyModel = ((BaseSubstitutionModel) values.getLast()).getFrequencyModel();
        Parameter pi = state.stateNodesByPhyloSpecName.get("pi");

        assertSame(pi, frequencyModel.getFrequencyParameter(), name + " copied the sampled frequencies.");

        double[] skewed = skewedFrequencies(size);
        setValues(pi, skewed);

        assertArrayEquals(skewed, frequencyModel.getFrequencies(), TOLERANCE);
    }

    /**
     * End-to-end likelihood check with a fixed tree: setting the sampled frequencies to the
     * model's empirical frequencies must reproduce the default model, which uses them as constants.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"jtt", "wag", "lg"})
    @Tag("beagle")
    public void likelihoodMatchesDefaultModelAtEmpiricalFrequencies(String name) throws Exception {
        BeastXModel sampled = buildModel(PROTEIN_DATA
                + "Simplex pi ~ Dirichlet(concentration=repeat(value=1.0, num=20))\n"
                + "Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=" + name + "(baseFrequencies=pi)) observed as data\n");

        BeastXModel defaultModel = buildModel(PROTEIN_DATA
                + "Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=" + name + "()) observed as data\n");

        setValues(sampled.beastState.stateNodesByPhyloSpecName.get("pi"), empiricalFrequencies(name));

        assertEquals(logDensity(defaultModel.likelihood), logDensity(sampled.likelihood), TOLERANCE,
                name + " likelihood uses stale frequencies.");
    }

    /**
     * The exported XML must reference the sampled {@code pi} parameter in the frequency model,
     * rather than an inline copy of its starting value, and BEAST X must be able to parse it.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"jtt", "wag", "lg"})
    public void xmlFrequencyModelReferencesSampledFrequencies(String name) throws Exception {
        Path xmlPath = XmlTestSupport.xmlPath("liveFrequencies-" + name);
        Path logPath = XmlTestSupport.logPath("liveFrequencies-" + name);
        XmlTestSupport.prepare(xmlPath, logPath);

        String source = """
                Alignment data = fromNexus("src/test/java/resources/protein-simple.nex")
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                Simplex pi ~ Dirichlet(concentration=repeat(value=1.0, num=20))
                Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=%s(baseFrequencies=pi)) observed as data

                mcmc {
                    Integer chainLength = 10
                    Logger fileLogger = fileLogger(logEvery=1, file="%s", parameters=[pi])
                }
                """.formatted(name, XmlTestSupport.unixPath(logPath));

        // Writes the XML and parses it with BEAST X.
        new PhyloSpecRunner(source).buildXmlRun(XmlRunnerOptions.builder("liveFrequencies", xmlPath).build());

        String xml = Files.readString(xmlPath);

        assertTrue(
                Pattern.compile("<frequencyModel [^>]*>\\s*<frequencies>\\s*<parameter idref=\"pi\"/>").matcher(xml).find(),
                name + " XML writes the frequencies inline instead of referencing pi:\n" + xml
        );
    }

    /** Runs the BEAST X tiling on the source and returns the value of each statement. */
    private static List<Object> applyTiles(String source, BeastXState state) {
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

        List<Object> values = new ArrayList<>();
        for (Tile<?, BeastXState> tile : evaluateTiles.getBestTiling(statements)) {
            values.add(tile.apply(state, new IdentityHashMap<>()));
        }
        return values;
    }

    private static double[] empiricalFrequencies(String name) {
        return switch (name) {
            case "jtt" -> JTT.INSTANCE.getEmpiricalFrequencies();
            case "wag" -> WAG.INSTANCE.getEmpiricalFrequencies();
            case "lg" -> LG.INSTANCE.getEmpiricalFrequencies();
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** A valid, clearly non-uniform simplex; the starting value is uniform. */
    private static double[] skewedFrequencies(int size) {
        double[] frequencies = new double[size];
        Arrays.fill(frequencies, 0.5 / (size - 1));
        frequencies[0] = 0.5;
        return frequencies;
    }

    private static BeastXModel buildModel(String source) throws Exception {
        try {
            return new PhyloSpecRunner(source).buildMaterializedModel("liveFrequencies");
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping frequency check because BEAGLE native library is not available."
            );
            throw exception;
        }
    }

    private static void setValues(Parameter parameter, double[] values) {
        for (int i = 0; i < values.length; i++) {
            parameter.setParameterValue(i, values[i]);
        }
    }

    private static double logDensity(Likelihood likelihood) {
        likelihood.makeDirty();
        return likelihood.getLogLikelihood();
    }
}
