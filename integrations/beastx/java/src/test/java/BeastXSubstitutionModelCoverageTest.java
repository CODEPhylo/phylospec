import dr.evolution.datatype.Codons;
import dr.evolution.datatype.GeneticCode;
import dr.evomodel.substmodel.EmpiricalRateMatrix;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.substmodel.aminoacid.JTT;
import dr.evomodel.substmodel.aminoacid.LG;
import dr.evomodel.substmodel.aminoacid.WAG;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tiling.BeastXState;
import tiling.runner.XmlRunnerOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Substitution models of BEAST X, checked by comparing the rate matrix Q that BEAST X uses inside a
 * PhyloCTMC with Q built here from the model's definition.
 *
 * <p>Reversible models are written as q(i, j) = s(i, j) * pi(j), scaled to one expected
 * substitution per unit time (core does not state the scaling; BEAST X and BEAST 3 both use it).
 * Each row is checked in memory and after an XML round trip, at states where its sampled
 * parameters change, so a frozen parameter also fails.</p>
 */
public class BeastXSubstitutionModelCoverageTest {

    private static final double TOLERANCE = 1e-9;

    private static final String NUCLEOTIDES = "Alignment data = fromNexus(\"src/test/java/resources/primate-mtDNA.nex\")\n";
    private static final String PROTEINS = "Alignment data = fromNexus(\"src/test/java/resources/protein-simple.nex\")\n";
    private static final String BINARY = "Alignment data = fromNexus(\"src/test/java/resources/binary-simple.nex\")\n";
    private static final double[] EQUAL = {0.25, 0.25, 0.25, 0.25};

    /** Values of sampled parameters, by name. */
    record State(Map<String, double[]> values) {
    }

    record Row(String data, String priors, String qMatrix, List<State> states, Function<State, double[]> expectedQ) {
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                row("jc69", NUCLEOTIDES, "", "jc69()",
                        List.of(state()),
                        s -> reversible(ones(6), EQUAL)),
                row("k80, sampled kappa", NUCLEOTIDES, "PositiveReal kappa ~ LogNormal(logMean=1.0, logSd=0.5)\n", "k80(kappa=kappa)",
                        List.of(state("kappa", 3.0), state("kappa", 0.7)),
                        s -> reversible(transitions(s.values().get("kappa")[0]), EQUAL)),
                row("f81", NUCLEOTIDES, "", "f81(baseFrequencies=[0.1, 0.2, 0.3, 0.4])",
                        List.of(state()),
                        s -> reversible(ones(6), new double[]{0.1, 0.2, 0.3, 0.4})),
                row("hky, sampled kappa and frequencies", NUCLEOTIDES,
                        """
                        PositiveReal kappa ~ LogNormal(logMean=1.0, logSd=0.5)
                        Simplex pi ~ Dirichlet(concentration=[2.0, 2.0, 2.0, 2.0])
                        """,
                        "hky(kappa=kappa, baseFrequencies=pi)",
                        List.of(state("kappa", 3.0, "pi", 0.1, 0.2, 0.3, 0.4), state("kappa", 0.7, "pi", 0.4, 0.3, 0.2, 0.1)),
                        s -> reversible(transitions(s.values().get("kappa")[0]), s.values().get("pi"))),
                row("gtr with six rates, sampled rateAG", NUCLEOTIDES, "PositiveReal ag ~ LogNormal(logMean=1.0, logSd=0.5)\n",
                        "gtr(rateAC=1.0, rateAG=ag, rateAT=0.5, rateCG=0.8, rateCT=3.0, rateGT=1.2, baseFrequencies=[0.1, 0.2, 0.3, 0.4])",
                        List.of(state("ag", 2.0), state("ag", 5.0)),
                        s -> reversible(new double[]{1.0, s.values().get("ag")[0], 0.5, 0.8, 3.0, 1.2}, new double[]{0.1, 0.2, 0.3, 0.4})),
                row("gtr with relative rates, sampled", NUCLEOTIDES,
                        "Simplex rates ~ Dirichlet(concentration=[2.0, 2.0, 2.0, 2.0, 2.0, 2.0])\n",
                        "gtr(relativeRates=rates, baseFrequencies=[0.1, 0.2, 0.3, 0.4])",
                        List.of(state("rates", 0.1, 0.2, 0.05, 0.08, 0.3, 0.27), state("rates", 0.3, 0.1, 0.1, 0.2, 0.2, 0.1)),
                        s -> reversible(s.values().get("rates"), new double[]{0.1, 0.2, 0.3, 0.4})),
                row("wag, default frequencies", PROTEINS, "", "wag()",
                        List.of(state()),
                        s -> empirical(WAG.INSTANCE, WAG.INSTANCE.getEmpiricalFrequencies())),
                row("jtt, sampled frequencies", PROTEINS,
                        "Simplex pi ~ Dirichlet(concentration=repeat(value=2.0, num=20))\n",
                        "jtt(baseFrequencies=pi)",
                        List.of(state("pi", aminoAcidFrequencies(1)), state("pi", aminoAcidFrequencies(3))),
                        s -> empirical(JTT.INSTANCE, s.values().get("pi"))),
                row("lg, default frequencies", PROTEINS, "", "lg()",
                        List.of(state()),
                        s -> empirical(LG.INSTANCE, LG.INSTANCE.getEmpiricalFrequencies())),
                row("mk, sampled rate", BINARY, "PositiveReal r ~ LogNormal(logMean=0.0, logSd=0.5)\n", "mk(rate=r)",
                        List.of(state("r", 2.0), state("r", 0.3)),
                        s -> mk(s.values().get("r")[0])),
                row("mk, default rate", BINARY, "", "mk()",
                        List.of(state()),
                        s -> mk(1.0))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    public void inMemoryRateMatrixMatchesDefinition(String name, Row row) throws Exception {
        BeastXState state = buildState(row.data() + row.priors() + phyloCtmc(row.qMatrix()));
        SubstitutionModel model = findSubstitutionModel(state.likelihoodDistributions);

        for (State values : row.states()) {
            values.values().forEach((id, v) -> setValues(state.stateNodesByPhyloSpecName.get(id), v));
            assertQ(name, row.expectedQ().apply(values), model);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    public void xmlRateMatrixMatchesDefinition(String name, Row row) throws Exception {
        Model posterior = parseXml(name, row.data() + row.priors() + phyloCtmc(row.qMatrix()));
        SubstitutionModel model = findSubstitutionModel(posterior);

        for (State values : row.states()) {
            values.values().forEach((id, v) -> setValues(findParameter(posterior, id), v));
            assertQ(name, row.expectedQ().apply(values), model);
        }
    }

    /**
     * GY94: a single-nucleotide change from codon i to codon j has rate pi(j), times kappa for a
     * transition and omega for a change of amino acid; other changes have rate 0. Full GY94
     * PhyloCTMC XML export is rejected (codon alignments cannot be written), so only memory is
     * checked.
     */
    @Test
    public void inMemoryGY94MatchesDefinition() throws Exception {
        BeastXState state = buildState("""
                Alignment fullData = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Alignment data = subset(alignment=fullData, start=1, end=600)
                PositiveReal kappa ~ LogNormal(logMean=1.0, logSd=0.5)
                PositiveReal omega ~ LogNormal(logMean=-0.5, logSd=0.5)
                Simplex pi ~ Dirichlet(concentration=repeat(value=1.0, num=61))
                """ + phyloCtmc("gy94(kappa=kappa, omega=omega, baseFrequencies=pi)"));
        SubstitutionModel model = findSubstitutionModel(state.likelihoodDistributions);

        double[] pi = codonFrequencies();
        setValues(state.stateNodesByPhyloSpecName.get("pi"), pi);
        for (double[] kappaOmega : new double[][]{{2.5, 0.4}, {1.2, 3.0}}) {
            state.stateNodesByPhyloSpecName.get("kappa").setParameterValue(0, kappaOmega[0]);
            state.stateNodesByPhyloSpecName.get("omega").setParameterValue(0, kappaOmega[1]);

            assertQ("gy94 kappa " + kappaOmega[0] + " omega " + kappaOmega[1], gy94(kappaOmega[0], kappaOmega[1], pi), model);
        }
    }

    // reference rate matrices

    /** Reversible Q from exchangeabilities in upper-triangle order (AC, AG, AT, CG, CT, GT for nucleotides). */
    private static double[] reversible(double[] exchangeabilities, double[] pi) {
        int n = pi.length;
        double[] q = new double[n * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                q[i * n + j] = exchangeabilities[k] * pi[j];
                q[j * n + i] = exchangeabilities[k] * pi[i];
                k++;
            }
        }
        return normalized(q, pi);
    }

    private static double[] empirical(EmpiricalRateMatrix matrix, double[] pi) {
        return reversible(matrix.getEmpiricalRates(), pi);
    }

    /** Binary Mk: both off-diagonal rates equal the expected rate of change. */
    private static double[] mk(double rate) {
        return new double[]{-rate, rate, rate, -rate};
    }

    private static double[] gy94(double kappa, double omega, double[] pi) {
        Codons codons = Codons.UNIVERSAL;
        GeneticCode code = codons.getGeneticCode();
        int n = codons.getStateCount();
        double[] q = new double[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int[] from = codons.getTripletStates(i);
                int[] to = codons.getTripletStates(j);
                int changes = 0;
                int position = -1;
                for (int p = 0; p < 3; p++) {
                    if (from[p] != to[p]) {
                        changes++;
                        position = p;
                    }
                }
                if (changes != 1) {
                    continue;
                }
                boolean transition = isTransition(from[position], to[position]);
                boolean synonymous = code.getAminoAcidState(codons.getCanonicalState(i))
                        == code.getAminoAcidState(codons.getCanonicalState(j));
                q[i * n + j] = pi[j] * (transition ? kappa : 1.0) * (synonymous ? 1.0 : omega);
            }
        }
        return normalized(q, pi);
    }

    /** Nucleotides are A, C, G, T (0 to 3): A-G and C-T are transitions. */
    private static boolean isTransition(int from, int to) {
        return (from == 0 && to == 2) || (from == 2 && to == 0) || (from == 1 && to == 3) || (from == 3 && to == 1);
    }

    /** Sets the diagonal and scales Q to one expected substitution per unit time. */
    private static double[] normalized(double[] q, double[] pi) {
        int n = pi.length;
        double meanRate = 0.0;
        for (int i = 0; i < n; i++) {
            double rowSum = 0.0;
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    rowSum += q[i * n + j];
                }
            }
            q[i * n + i] = -rowSum;
            meanRate += pi[i] * rowSum;
        }
        for (int i = 0; i < q.length; i++) {
            q[i] /= meanRate;
        }
        return q;
    }

    private static double[] transitions(double kappa) {
        return new double[]{1.0, kappa, 1.0, 1.0, kappa, 1.0};
    }

    private static double[] ones(int n) {
        double[] values = new double[n];
        java.util.Arrays.fill(values, 1.0);
        return values;
    }

    /** Uneven frequencies that sum to 1. */
    private static double[] aminoAcidFrequencies(int seed) {
        return unevenFrequencies(20, seed);
    }

    private static double[] codonFrequencies() {
        return unevenFrequencies(61, 2);
    }

    private static double[] unevenFrequencies(int n, int seed) {
        double[] values = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            values[i] = 1.0 + ((i * seed) % 7);
            sum += values[i];
        }
        for (int i = 0; i < n; i++) {
            values[i] /= sum;
        }
        return values;
    }

    // helpers

    private static Arguments row(String name, String data, String priors, String qMatrix, List<State> states,
                                 Function<State, double[]> expectedQ) {
        return Arguments.of(name, new Row(data, priors, qMatrix, states, expectedQ));
    }

    private static State state(Object... nameAndValues) {
        Map<String, double[]> values = new java.util.LinkedHashMap<>();
        String name = null;
        List<Double> current = new java.util.ArrayList<>();
        for (Object item : nameAndValues) {
            if (item instanceof String s) {
                if (name != null) {
                    values.put(name, current.stream().mapToDouble(Double::doubleValue).toArray());
                }
                name = s;
                current = new java.util.ArrayList<>();
            } else if (item instanceof double[] array) {
                for (double v : array) {
                    current.add(v);
                }
            } else {
                current.add(((Number) item).doubleValue());
            }
        }
        if (name != null) {
            values.put(name, current.stream().mapToDouble(Double::doubleValue).toArray());
        }
        return new State(values);
    }

    private static String phyloCtmc(String qMatrix) {
        return """
                Taxa taxa = taxa(data)
                Tree tree ~ Yule(birthRate=1.0, taxa=taxa)
                QMatrix q = %s
                Alignment alignment ~ PhyloCTMC(tree=tree, qMatrix=q) observed as data
                """.formatted(qMatrix);
    }

    private static void assertQ(String name, double[] expected, SubstitutionModel model) {
        double[] actual = new double[expected.length];
        model.getInfinitesimalMatrix(actual);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], TOLERANCE, name + ", entry " + i);
        }
    }

    private static void setValues(Parameter parameter, double[] values) {
        for (int i = 0; i < values.length; i++) {
            parameter.setParameterValue(i, values[i]);
        }
    }

    private static BeastXState buildState(String source) throws Exception {
        try {
            return new PhyloSpecRunner(source).buildState("substitution");
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    private static Model parseXml(String name, String source) throws Exception {
        String fileName = "substitution-" + name.replaceAll("\\W+", "-");
        Path xmlPath = XmlTestSupport.xmlPath(fileName);
        Path treeLogPath = XmlTestSupport.treeLogPath(fileName);
        XmlTestSupport.prepare(xmlPath, treeLogPath);

        try {
            return new PhyloSpecRunner(source + """

                    mcmc {
                        Integer chainLength = 1
                        Logger treeLogger = treeLogger(logEvery=1, file="%s", trees=[tree])
                    }
                    """.formatted(XmlTestSupport.unixPath(treeLogPath)))
                    .buildXmlRun(XmlRunnerOptions.builder("substitution", xmlPath).build())
                    .mcmc()
                    .getLikelihood()
                    .getModel();
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(XmlTestSupport.isMissingBeagleLibrary(exception), "BEAGLE native library is not available.");
            throw exception;
        }
    }

    /** Finds the substitution model among the models of the given likelihoods. */
    private static SubstitutionModel findSubstitutionModel(List<? extends Likelihood> likelihoods) {
        for (Likelihood likelihood : likelihoods) {
            SubstitutionModel model = findSubstitutionModel(likelihood.getModel());
            if (model != null) {
                return model;
            }
        }
        throw new AssertionError("No substitution model found.");
    }

    private static SubstitutionModel findSubstitutionModel(Model model) {
        if (model instanceof SubstitutionModel substitutionModel) {
            return substitutionModel;
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            SubstitutionModel found = findSubstitutionModel(model.getModel(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Finds a parameter by id among the variables of a model and its sub-models. */
    private static Parameter findParameter(Model model, String id) {
        for (int i = 0; i < model.getVariableCount(); i++) {
            if (model.getVariable(i) instanceof Parameter parameter && id.equals(parameter.getId())) {
                return parameter;
            }
        }
        for (int i = 0; i < model.getModelCount(); i++) {
            Parameter parameter = findParameter(model.getModel(i), id);
            if (parameter != null) {
                return parameter;
            }
        }
        return null;
    }
}
