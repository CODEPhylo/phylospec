import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.Frequencies;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.Beta;
import beast.base.inference.distribution.Dirichlet;
import beast.base.inference.distribution.LogNormalDistributionModel;
import beast.base.inference.parameter.RealParameter;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.w3c.dom.Element;
import phylonco.beast.evolution.datatype.NucleotideDiploid16;
import phylonco.beast.evolution.errormodel.GT16ErrorModel;
import phylonco.beast.evolution.likelihood.TreeLikelihoodWithErrorFast;
import phylonco.beast.evolution.substitutionmodel.GT16;
import popfunc.beast.evolution.populationmodel.ConstantGrowth;
import popfunc.beast.evolution.populationmodel.ExponentialGrowth;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.LogisticGrowth;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;
import runner.PhyloSpecRunner;

/**
 * Cross-package test against PopFunc-paper commit 10b0f82db2da9aa5f48fc84f231128bfc98e115e,
 * real/L86/data/L86.xml. Like CatsAnalysis, this needs no new Maven dependency on Phylonco.
 * Native objects are constructed directly with current engine APIs; the old coupled-MCMC XML
 * itself is not executed. This verifies wiring/densities, not historical engine equivalence.
 */
class L86Analysis {
    private static final List<String> LIBRARIES = List.of("popfunc", "phylonco", "beast2");
    private static final List<String> SCALARS = List.of("N0_con", "N_max", "b", "t50", "f0", "N0", "NA", "delta", "epsilon");
    private static final double TOLERANCE = 1e-7;
    private static final Map<String, Element> XML = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: L86Analysis.java reference-directory new-output-directory");
        System.setProperty("java.only", "true");
        Path sourceDirectory = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectory(output); // Never overwrite a previous analysis.
        readReference(sourceDirectory.resolve("reference.xml"), output);
        Path script = output.resolve("analysis.phylospec");
        Files.copy(sourceDirectory.resolve("analysis.phylospec"), script);
        BEASTState graph = build(Files.readString(script), output);
        require(graph.stateNodes.size() == 14 && graph.priorDistributions.size() == 14,
                "Expected 11 continuous parameters, two indicators and one tree, each with one prior; nodes="
                        + graph.stateNodes.keySet().stream().map(StateNode::getID).toList() + "; priors=" + graph.priorDistributions.size());
        require(graph.stateNodes.keySet().equals(graph.priorDistributions.keySet()), "Missing or duplicate prior ownership");
        require(graph.likelihoodDistributions.size() == 1, "GT16 observation must contribute one likelihood");
        var likelihood = (TreeLikelihoodWithErrorFast) graph.likelihoodDistributions.getFirst();
        require(likelihood.dataInput.get().getTaxonCount() == 32 && likelihood.dataInput.get().getSiteCount() == 598,
                "Wrong L86 dataset");
        checkAlignment(likelihood.dataInput.get());
        require(Boolean.TRUE.equals(likelihood.getInput("useAmbiguities").get())
                && Boolean.TRUE.equals(likelihood.getInput("useTipLikelihoods").get()), "Lost error/ambiguity settings");
        var outer = (StochasticVariableSelection) coalescent(graph).popSizeInput.get();
        require(outer.modelsInput.get().size() == 2 && outer.indicatorInput.get() == named(graph, "I_na"), "Wrong ancestral selection");
        for (var model : outer.modelsInput.get()) {
            var inner = (StochasticVariableSelection) model;
            require(inner.modelsInput.get().size() == 4 && inner.indicatorInput.get() == named(graph, "I"), "Model index must be shared");
            var candidates = inner.modelsInput.get();
            // Core and PopFunc both publish these canonical generators. Either implementation
            // is valid without an ancestor; verify shared inputs rather than force a Tile choice.
            Object constantSize = candidates.get(0) instanceof ConstantGrowth constant
                    ? constant.popSizeParameterInput.get()
                    : ((beast.base.spec.evolution.tree.coalescent.ConstantPopulation) candidates.get(0)).popSizeParameter.get();
            require(constantSize == named(graph, "N0_con"), "Unshared constant size");
            Object exponentialSize;
            Object exponentialRate;
            if (candidates.get(1) instanceof ExponentialGrowth exponential) {
                exponentialSize = exponential.popSizeParameterInput.get();
                exponentialRate = exponential.growthRateParameterInput.get();
            } else {
                require(model == outer.modelsInput.get().getFirst(), "Ancestral exponential requires PopFunc");
                var exponential = (beast.base.spec.evolution.tree.coalescent.ExponentialGrowth) candidates.get(1);
                exponentialSize = exponential.popSizeParameterInput.get();
                exponentialRate = exponential.growthRateParameterInput.get();
            }
            var logistic = (LogisticGrowth) candidates.get(2);
            var gompertz = (GompertzGrowth_f0) candidates.get(3);
            require(exponentialSize == named(graph, "N_max")
                    && logistic.nCarryingCapacityInput.get() == named(graph, "N_max"), "Unshared carrying capacity");
            require(exponentialRate == named(graph, "b")
                    && logistic.bInput.get() == named(graph, "b") && gompertz.bInput.get() == named(graph, "b"), "Unshared growth rate");
            require(logistic.t50Input.get() == named(graph, "t50") && gompertz.N0Input.get() == named(graph, "N0")
                    && gompertz.f0Input.get() == named(graph, "f0"), "Lost demographic binding");
        }
        var factors = new ArrayList<>(graph.priorDistributions.values());
        factors.add(likelihood);
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        var state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        matchXmlPoint(graph);
        int checks = 0;
        for (int model = 0; model < 4; model++) {
            for (int ancestor = 0; ancestor < 2; ancestor++) {
                integer(graph, "I").set(model);
                integer(graph, "I_na").set(ancestor);
                state.robustlyCalcPosterior(posterior);
                assertReference(graph, likelihood, posterior);
                checks++;
            }
        }
        // Every state variable gets both an acceptance and a rejection check.
        var targets = new ArrayList<>(SCALARS);
        targets.addAll(List.of("pi", "rates", "tree", "I", "I_na"));
        for (String target : targets) {
            for (boolean accept : List.of(false, true)) {
                matchXmlPoint(graph);
                // Exercise each demographic parameter while its model is selected,
                // otherwise only its prior (not the coalescent) would be tested.
                integer(graph, "I").set(switch (target) {
                    case "N0_con" -> 0;
                    case "N_max" -> 1;
                    case "t50" -> 2;
                    default -> 3;
                });
                integer(graph, "I_na").set(1);
                double before = state.robustlyCalcPosterior(posterior);
                state.store(1);
                change(graph, target);
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                double proposed = posterior.calculateLogP();
                assertReference(graph, likelihood, posterior);
                if (accept) state.acceptCalculationNodes();
                else {
                    state.restore();
                    state.restoreCalculationNodes();
                }
                state.setEverythingDirty(false);
                equal("restore " + target, accept ? proposed : before, posterior.getCurrentLogP());
                assertReference(graph, likelihood, posterior);
                equal("recalculate " + target, accept ? proposed : before, state.robustlyCalcPosterior(posterior));
                checks++;
            }
        }
        System.out.println("PASS: " + checks + " reference checks (8 indicator states + 28 accept/restore cases)");
        // Separate run with ordinary Tile initialization and the unchanged priors.
        new PhyloSpecRunner(script, LIBRARIES).runPhyloSpec(output.resolve("analysis").toString());
        checkTrace(output);
        System.out.println("PASS: 32-taxon/598-site PopFunc + Phylonco 30-step analysis: " + output);
    }

    private static void readReference(Path path, Path output) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        require(sha.equals("645b3ac9a90d6ec153fe07fcc096c12601c6d9f8f7190fbd1d0f6c1ed39f7c8a"),
                "Reference XML differs from the pinned paper configuration");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(path.toFile());
        var elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            var element = (Element) elements.item(i);
            if (element.hasAttribute("id")) require(XML.put(element.getAttribute("id"), element) == null, "Duplicate XML id");
        }
        var sequences = XML.get("D").getElementsByTagName("sequence");
        require(sequences.getLength() == 32, "Expected the paper XML's 32 sequences");
        var names = new java.util.HashSet<String>();
        StringBuilder matrix = new StringBuilder();
        for (int i = 0; i < sequences.getLength(); i++) {
            Element sequence = (Element) sequences.item(i);
            String value = sequence.hasAttribute("value") ? sequence.getAttribute("value") : sequence.getTextContent();
            value = value.replaceAll("\\s+", "");
            String taxon = sequence.getAttribute("taxon");
            require(value.length() == 598 && names.add(taxon) && taxon.matches("[A-Za-z0-9_-]+"), "Invalid sequence/taxon " + taxon);
            matrix.append(taxon).append(' ').append(value).append('\n');
        }
        Files.writeString(output.resolve("l86.nex"), "#NEXUS\nbegin data;\ndimensions ntax=32 nchar=598;\n"
                + "format datatype=nucleotideDiploid16 missing=? gap=-;\nmatrix\n" + matrix + ";\nend;\n");
        var models = XML.get("SVSmodel").getElementsByTagName("models");
        require(models.getLength() == 4, "Expected four reference models");
        require(XML.get("I").getAttribute("upper").equals("3") && XML.get("I_na").getAttribute("upper").equals("1"), "Changed indicators");
        require(XML.get("mcmcmc").getAttribute("spec").equals("coupledMCMC.CoupledMCMC"), "Changed reference execution");
        Files.writeString(output.resolve("reference.sha256"), sha + "  reference.xml\n");
    }

    private static void matchXmlPoint(BEASTState graph) {
        for (String name : SCALARS) ((RealScalarParam<?>) named(graph, name)).set(Double.parseDouble(XML.get(name).getTextContent().trim()));
        for (String name : List.of("pi", "rates")) {
            double[] values = values(XML.get(name));
            var vector = (SimplexParam) named(graph, name);
            for (int i = 0; i < values.length; i++) vector.set(i, values[i]);
        }
    }

    private static void checkAlignment(Alignment alignment) {
        require(alignment.getDataType() instanceof NucleotideDiploid16, "Wrong GT16 datatype");
        var datatype = new NucleotideDiploid16();
        var sequences = XML.get("D").getElementsByTagName("sequence");
        for (int i = 0; i < sequences.getLength(); i++) {
            Element sequence = (Element) sequences.item(i);
            String taxon = sequence.getAttribute("taxon");
            String value = sequence.hasAttribute("value") ? sequence.getAttribute("value") : sequence.getTextContent();
            int index = alignment.getTaxonIndex(taxon);
            require(index >= 0, "Missing L86 taxon " + taxon);
            require(datatype.stringToEncoding(value.replaceAll("\\s+", "")).equals(alignment.getCounts().get(index)),
                    "Changed L86 sequence for " + taxon);
        }
    }

    private static void assertReference(BEASTState graph, TreeLikelihoodWithErrorFast actual, CompoundDistribution posterior) {
        Tree tree = new Tree(((Tree) named(graph, "tree")).getRoot().copy());
        var population = nativePopulation(graph);
        var reference = new Coalescent();
        reference.initByName("tree", tree, "populationModel", population);
        double treeDensity = reference.calculateLogP();
        factor(graph, "tree", treeDensity);
        var selected = coalescent(graph).popSizeInput.get();
        for (double time : new double[]{0.0, tree.getRoot().getHeight() / 2, tree.getRoot().getHeight()}) {
            equal("population at " + time, population.getPopSize(time), selected.getPopSize(time));
            equal("intensity at " + time, population.getIntensity(time), selected.getIntensity(time));
        }
        double prior = treeDensity;
        for (String name : SCALARS) {
            Element law = (Element) XML.get(name + ".prior").getElementsByTagName("distr").item(0);
            var parameters = law.getElementsByTagName("parameter");
            double a = Double.parseDouble(parameters.item(0).getTextContent().trim());
            double b = Double.parseDouble(parameters.item(1).getTextContent().trim());
            double density;
            if (law.getAttribute("spec").endsWith("LogNormalDistributionModel")) {
                var distribution = new LogNormalDistributionModel();
                distribution.initByName("M", Double.toString(a), "S", Double.toString(b));
                density = distribution.logDensity(scalar(graph, name));
            } else {
                require(law.getAttribute("spec").equals("distribution.Beta"), "Unexpected prior for " + name);
                var distribution = new Beta();
                distribution.initByName("alpha", Double.toString(a), "beta", Double.toString(b));
                density = distribution.logDensity(scalar(graph, name));
            }
            factor(graph, name, density);
            prior += density;
        }
        for (String name : List.of("pi", "rates")) {
            var distribution = new Dirichlet();
            var alpha = (Element) XML.get(name + ".prior").getElementsByTagName("parameter").item(0);
            distribution.initByName("alpha", new RealParameter(Arrays.stream(values(alpha)).boxed().toArray(Double[]::new)));
            double density = distribution.calcLogP(vector(graph, name));
            factor(graph, name, density);
            prior += density;
        }
        factor(graph, "I", -Math.log(4));
        factor(graph, "I_na", -Math.log(2));
        // XML has implicit bounded uniform factors. Explicit normalized priors add -log(8).
        prior -= Math.log(8);
        var frequencies = new Frequencies();
        frequencies.initByName("frequencies", vector(graph, "pi"));
        var rates = vector(graph, "rates");
        rates.keysInput.setValue("AC AG AT CG CT GT", rates);
        rates.initAndValidate();
        var substitution = new GT16();
        substitution.initByName("nucRates", rates, "frequencies", frequencies);
        var sites = new SiteModel();
        sites.initByName("substModel", substitution);
        var errors = new GT16ErrorModel();
        errors.initByName("epsilon", Double.toString(scalar(graph, "epsilon")), "delta", Double.toString(scalar(graph, "delta")),
                "datatype", new NucleotideDiploid16());
        var clock = new StrictClockModel();
        clock.initByName("clock.rate", "1.0");
        var likelihood = new TreeLikelihoodWithErrorFast();
        likelihood.initByName("data", actual.dataInput.get(), "tree", tree, "siteModel", sites,
                "branchRateModel", clock, "errorModel", errors, "useAmbiguities", true, "useTipLikelihoods", true);
        double density = likelihood.calculateLogP();
        equal("GT16 likelihood", density, actual.getCurrentLogP());
        equal("posterior (explicit indicator normalization)", prior + density, posterior.getCurrentLogP());
    }

    private static PopulationFunction nativePopulation(BEASTState graph) {
        int indicator = integer(graph, "I").get();
        int ancestor = integer(graph, "I_na").get();
        PopulationFunction.Abstract model;
        switch (indicator) {
            case 0 -> {
                model = new ConstantGrowth();
                model.initByName("N0", positive(scalar(graph, "N0_con")));
            }
            case 1 -> {
                model = new ExponentialGrowth();
                model.initByName("N0", positive(scalar(graph, "N_max")), "GrowthRate", positive(scalar(graph, "b")),
                        "NA", positive(scalar(graph, "NA")), "I_na", new IntScalarParam<>(ancestor, NonNegativeInt.INSTANCE));
            }
            case 2 -> {
                model = new LogisticGrowth();
                model.initByName("nCarryingCapacity", positive(scalar(graph, "N_max")), "t50", positive(scalar(graph, "t50")),
                        "b", positive(scalar(graph, "b")), "NA", positive(scalar(graph, "NA")),
                        "I_na", new RealScalarParam<>((double) ancestor, beast.base.spec.domain.NonNegativeReal.INSTANCE));
            }
            case 3 -> {
                model = new GompertzGrowth_f0();
                model.initByName("f0", positive(scalar(graph, "f0")), "N0", positive(scalar(graph, "N0")),
                        "b", positive(scalar(graph, "b")), "NA", positive(scalar(graph, "NA")),
                        "I_na", new IntScalarParam<>(ancestor, NonNegativeInt.INSTANCE));
            }
            default -> throw new AssertionError("Invalid model index " + indicator);
        }
        return model;
    }

    private static void change(BEASTState graph, String target) {
        switch (target) {
            case "I" -> integer(graph, target).set(2);
            case "I_na" -> integer(graph, target).set(0);
            case "tree" -> {
                var tree = (Tree) named(graph, target);
                tree.getRoot().setHeight(tree.getRoot().getHeight() * 1.05);
            }
            case "pi", "rates" -> {
                var vector = (SimplexParam) named(graph, target);
                vector.set(0, vector.get(0) + 0.001);
                vector.set(1, vector.get(1) - 0.001);
            }
            default -> ((RealScalarParam<?>) named(graph, target)).set(scalar(graph, target) * 1.01);
        }
    }

    private static void checkTrace(Path output) throws Exception {
        var lines = Files.readAllLines(output.resolve("analysis.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        require(lines.size() == 32, "Incomplete 30-step trace");
        var header = List.of(lines.getFirst().trim().split("\\s+"));
        require(header.containsAll(SCALARS) && header.containsAll(List.of("I", "I_na", "prior", "likelihood", "posterior")), "Missing trace columns");
        for (int sample = 0; sample <= 30; sample++) {
            String[] row = lines.get(sample + 1).trim().split("\\s+");
            require(row.length == header.size() && Integer.parseInt(row[0]) == sample, "Incomplete sample " + sample);
            for (String entry : row) require(Double.isFinite(Double.parseDouble(entry)), "Non-finite trace value");
            double i = Double.parseDouble(row[header.indexOf("I")]);
            double a = Double.parseDouble(row[header.indexOf("I_na")]);
            require(i >= 0 && i <= 3 && i == Math.rint(i) && (a == 0 || a == 1), "Invalid indicator state");
            equal("trace sum", Double.parseDouble(row[header.indexOf("prior")]) + Double.parseDouble(row[header.indexOf("likelihood")]),
                    Double.parseDouble(row[header.indexOf("posterior")]));
        }
        require(Files.readString(output.resolve("analysis.trees")).contains("STATE_30"), "Missing final tree");
        require(Files.readString(output.resolve("analysis.state.xml")).split("<statenode ", -1).length - 1 == 14, "Wrong final state size");
    }

    private static BEASTState build(String source, Path directory) throws Exception {
        Randomizer.setSeed(12345);
        var catalog = TileCatalog.select(BEASTState.class, LIBRARIES);
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> { throw new AssertionError(error.toStdOutString(source)); });
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> { throw new AssertionError(error.toStdOutString(source)); });
        var statements = new EvaluateScalarFunctions().transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("popfunc-l86", directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private static Coalescent coalescent(BEASTState graph) {
        return (Coalescent) graph.priorDistributions.get(named(graph, "tree"));
    }

    private static StateNode named(BEASTState graph, String name) {
        return graph.stateNodes.keySet().stream().filter(node -> name.equals(node.getID())).findFirst().orElseThrow();
    }

    private static double scalar(BEASTState graph, String name) {
        return ((RealScalar<?>) named(graph, name)).get();
    }

    private static IntScalarParam<?> integer(BEASTState graph, String name) {
        return (IntScalarParam<?>) named(graph, name);
    }

    private static RealScalarParam<PositiveReal> positive(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }

    private static RealParameter vector(BEASTState graph, String name) {
        return new RealParameter(((RealVector<?>) named(graph, name)).getElements().toArray(Double[]::new));
    }

    private static double[] values(Element element) {
        return Arrays.stream(element.getTextContent().trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
    }

    private static void factor(BEASTState graph, String name, double expected) {
        equal(name + " prior", expected, graph.priorDistributions.get(named(graph, name)).getCurrentLogP());
    }

    private static void equal(String label, double expected, double actual) {
        require(Double.isFinite(expected) && Double.isFinite(actual) && Math.abs(expected - actual) <= TOLERANCE,
                label + ": expected " + expected + ", actual " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
