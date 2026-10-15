import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.operator.WilsonBalding;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.MCMC;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.parser.XMLParser;
import beast.base.spec.evolution.likelihood.ThreadedTreeLikelihood;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.tree.MRCAPrior;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import calibratedcpp.CalibratedBirthDeathSkylineModel;
import calibrationprior.CalibrationCladePrior;
import calibrationprior.CalibrationPrior;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import mixture.beast.evolution.mixture.SharedRatesClockModelSpec;
import mixture.beast.evolution.util.BranchRateIndexHelper;
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
import runner.PhyloSpecRunner;

/**
 * Executable regression for the cats XML analyses, not a general XML converter.
 * The P3 acceptance target is cats-half_uniform_mrca_not_conditioned_b3.xml.
 * Run with the CalibratedCPP runtime bundle and the ClockModelAveraging adapter/engine jars.
 * The source launcher keeps this cross-package check out of the core's Maven dependencies.
 */
class CatsAnalysis {
    private static final List<String> LIBRARIES = List.of("calibratedcpp", "clockmodelaveraging", "beast2");
    private static final List<String> SCALARS = List.of(
            "rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "diversificationRate", "mu", "sigma", "mRate", "gamma");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: CatsAnalysis.java reference.xml new-output-directory");
        System.setProperty("java.only", "true");
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectory(output); // Refuse to overwrite previous scientific results.
        XMLParser parser = new XMLParser();
        MCMC reference = (MCMC) parser.parseFile(Path.of(args[0]).toFile());
        reference.startStateInput.get().robustlyCalcPosterior(reference.posteriorInput.get());
        var ids = parser.getIDMap();
        var likelihood = (ThreadedTreeLikelihood) ids.get("D.treeLikelihood");
        var process = (CalibratedBirthDeathSkylineModel) ids.get("CalibratedCoalescentPointProcess");
        var tree = (Tree) ids.get("tree");
        var joint = (CalibrationPrior) ids.get("CalibrationPrior");
        var prior = (CompoundDistribution) ids.get("prior");
        require(likelihood.useAmbiguitiesInput.get(), "Expected ambiguity-aware likelihood");
        require(tree.getLeafNodeCount() == 19 && likelihood.dataInput.get().getSiteCount() == 30141,
                "Expected the 19-taxon, 30141-site cats reference");
        Path alignment = output.resolve("cats.nex");
        writeAlignment(likelihood.dataInput.get(), alignment);
        List<String> clades = new ArrayList<>();
        List<String> names = new ArrayList<>();
        double calibrationLogP = 0;
        if (joint != null) {
            for (CalibrationCladePrior clade : joint.cladesInput.get()) {
                addClade(clades, names, clade.getTaxa().asStringList(),
                        clade.getLower(), clade.getUpper(), clade.getCoverage());
            }
            calibrationLogP = joint.calculateLogP();
        } else {
            for (var distribution : prior.pDistributions.get()) {
                if (distribution instanceof MRCAPrior mrca) {
                    var uniform = (Uniform) mrca.distInput.get();
                    addClade(clades, names, mrca.taxonsetInput.get().asStringList(),
                            uniform.lowerInput.get().get(), uniform.upperInput.get().get(), 0.9);
                    calibrationLogP += mrca.calculateLogP();
                }
            }
        }
        require(names.size() == 4, "Expected four calibration clades");
        String source = model(clades, names, tree, joint != null,
                process.isConditionOnCalibrations(), ((RealScalar<?>) ids.get("rho")).get());
        Path script = output.resolve("analysis.phylospec");
        Files.writeString(script, source);
        BEASTState actual = evaluate(source, output);
        Map<String, StateNode> nodes = actual.stateNodes.keySet().stream()
                .collect(Collectors.toMap(StateNode::getID, node -> node));
        require(nodes.size() == 13 && actual.priorDistributions.size() == 13,
                "Expected ten scalars, frequencies, raw rates and tree, each with one prior");
        for (String name : SCALARS) {
            ((RealScalarParam<?>) nodes.get(name)).set(((RealScalar<?>) ids.get(name)).get());
        }
        var frequencies = (RealVectorParam<?>) nodes.get("pi");
        var referenceFrequencies = (RealVectorParam<?>) ids.get("pi");
        for (int i = 0; i < frequencies.size(); i++) frequencies.set(i, referenceFrequencies.get(i));
        var actualLikelihood = (TreeLikelihood) actual.likelihoodDistributions.getFirst();
        var actualAlignment = actualLikelihood.dataInput.get();
        var referenceAlignment = likelihood.dataInput.get();
        require(actualAlignment.getTaxonCount() == referenceAlignment.getTaxonCount()
                && actualAlignment.getSiteCount() == referenceAlignment.getSiteCount(), "Changed alignment dimensions");
        for (String taxon : referenceAlignment.getTaxaNames()) {
            int index = actualAlignment.getTaxonIndex(taxon);
            require(index >= 0, "Missing cats taxon " + taxon);
            require(referenceAlignment.getSequenceAsString(taxon).equals(actualAlignment.getSequenceAsString(taxon)),
                    "Changed cats sequence for " + taxon);
        }
        var clock = (SharedRatesClockModelSpec) actualLikelihood.branchRateModelInput.get();
        var actualTree = clock.treeInput.get();
        var rates = (RealVectorParam<?>) nodes.get("branchRates");
        require(rates.size() == 36, "Continuous clock requires 36 branch multipliers");
        var referenceBranches = branches(tree);
        var actualBranches = branches(actualTree);
        require(referenceBranches.keySet().equals(actualBranches.keySet()), "Topology/taxa changed");
        var indices = BranchRateIndexHelper.buildDeterministic(actualTree);
        double meanRate = ((RealScalar<?>) ids.get("mRate")).get();
        for (var entry : actualBranches.entrySet()) {
            Node node = entry.getValue();
            Node expected = referenceBranches.get(entry.getKey());
            equal("branch length " + entry.getKey(), expected.getLength(), node.getLength(), 1e-10);
            rates.set(indices.idxForNode(node), likelihood.branchRateModelInput.get().getRateForBranch(expected) / meanRate);
        }
        var actualPrior = new CompoundDistribution();
        actualPrior.initByName("distribution", new ArrayList<>(actual.priorDistributions.values()));
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(actualPrior, actualLikelihood));
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(actual.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        double obtained = state.robustlyCalcPosterior(posterior);
        List<String> parameterNames = new ArrayList<>(SCALARS);
        parameterNames.addAll(List.of("pi", "branchRates"));
        for (String name : parameterNames) {
            equal(name + " prior", ((Distribution) ids.get(name + ".prior")).calculateLogP(),
                    actual.priorDistributions.get(nodes.get(name)).getCurrentLogP(), 1e-9);
        }
        var treePrior = (CompoundDistribution) actual.priorDistributions.get(nodes.get("tree"));
        equal("CPP", process.calculateLogP(), treePrior.pDistributions.get().get(0).getCurrentLogP(), 1e-9);
        equal("calibration", calibrationLogP, treePrior.pDistributions.get().get(1).getCurrentLogP(), 1e-9);
        for (var entry : actualBranches.entrySet()) {
            equal("clock " + entry.getKey(), likelihood.branchRateModelInput.get().getRateForBranch(referenceBranches.get(entry.getKey())),
                    clock.getRateForBranch(entry.getValue()), 1e-12);
        }
        equal("prior", prior.calculateLogP(), actualPrior.getCurrentLogP(), 1e-8);
        equal("likelihood", likelihood.calculateLogP(), actualLikelihood.getCurrentLogP(), 1e-8);
        equal("posterior", reference.posteriorInput.get().calculateLogP(), obtained, 1e-8);

        Operator treeOperator = actual.operators.stream().filter(WilsonBalding.class::isInstance)
                .findFirst().orElseThrow();
        verifyTreeRejection(state, posterior, actualTree, treeOperator, obtained);

        // This independent run uses normal Tile initial values, not the matched XML state above.
        new PhyloSpecRunner(script, LIBRARIES).runPhyloSpec(output.resolve("analysis").toString());
        checkTrace(output);
        System.out.println("PASS: full cats posterior comparison and 200-step sequence analysis: " + output);
    }

    private static void verifyTreeRejection(State state, Distribution posterior, Tree tree,
            Operator operator, double initialPosterior) {
        Randomizer.setSeed(12345);
        String initialTree = tree.getRoot().toNewick();
        int validProposals = 0;
        for (int sample = 0; sample < 100; sample++) {
            state.store(sample);
            double hastings = operator.proposal();
            if (hastings != Double.NEGATIVE_INFINITY) {
                require(Double.isFinite(hastings), "Invalid Hastings ratio at " + sample);
                validProposals++;
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                posterior.calculateLogP();
            }
            state.restore();
            if (hastings != Double.NEGATIVE_INFINITY) state.restoreCalculationNodes();
            state.setEverythingDirty(false);
            require(initialTree.equals(tree.getRoot().toNewick()), "Tree not restored at " + sample);
            equal("cached restored posterior " + sample, initialPosterior, posterior.getCurrentLogP(), 1e-8);
            equal("restored posterior " + sample, initialPosterior, state.robustlyCalcPosterior(posterior), 1e-8);
        }
        require(validProposals > 0, "No valid tree proposal was tested");
        System.out.println("PASS: " + validProposals + " valid tree proposals among 100 rejection/restore checks");
    }

    private static void checkTrace(Path output) throws Exception {
        var lines = Files.readAllLines(output.resolve("analysis.log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        require(lines.size() == 22, "Incomplete 200-step trace");
        var header = List.of(lines.getFirst().trim().split("\\s+"));
        require(header.containsAll(SCALARS) && header.containsAll(List.of("prior", "likelihood", "posterior")),
                "Missing trace columns");
        var posteriors = new ArrayList<Double>();
        for (int sample = 0; sample <= 20; sample++) {
            String[] row = lines.get(sample + 1).trim().split("\\s+");
            require(row.length == header.size() && Integer.parseInt(row[0]) == sample * 10,
                    "Incomplete sample " + sample * 10);
            for (String entry : row) require(Double.isFinite(Double.parseDouble(entry)), "Non-finite trace value");
            double posterior = Double.parseDouble(row[header.indexOf("posterior")]);
            equal("trace sum " + sample * 10,
                    Double.parseDouble(row[header.indexOf("prior")]) + Double.parseDouble(row[header.indexOf("likelihood")]),
                    posterior, 1e-8);
            posteriors.add(posterior);
        }
        require(posteriors.stream().distinct().count() > 1, "Posterior did not change");
        require(Files.readString(output.resolve("analysis.trees")).contains("STATE_200"), "Missing final tree");
        require(Files.readString(output.resolve("analysis.state.xml")).split("<statenode ", -1).length - 1 == 13,
                "Wrong final state size");
    }

    private static String model(List<String> clades, List<String> names, Tree tree,
            boolean joint, boolean conditioned, double rho) {
        return """
                use calibratedcpp.types
                use calibratedcpp.functions
                use calibratedcpp.distributions.tree
                use clockmodelaveraging.functions.clock
                data {
                  Alignment cats = fromNexus(file="cats.nex")
                }
                model {
                  %s
                  CalibrationForest calibrations = calibrationForest(clades=[%s])
                  PositiveReal diversificationRate ~ LogNormal(logMean=0.05,logSd=1.0)
                  Probability mu ~ Beta(alpha=0.05,beta=1.0)
                  Tree tree ~ CalibratedBirthDeathSkyline(
                    diversificationRate=skylineParameter(values=[diversificationRate]),
                    turnover=skylineParameter(values=[mu]),rho=%s,
                    calibrations=calibrations,calibrationMode="%s",conditionOnCalibrations=%s,
                    initialNewick="%s")
                  Simplex pi ~ Dirichlet(concentration=[3.0,3.0,3.0,3.0])
                  PositiveReal rateAC ~ LogNormal(logMean=0.0,logSd=1.0)
                  PositiveReal rateAG ~ LogNormal(logMean=0.0,logSd=1.0)
                  PositiveReal rateAT ~ LogNormal(logMean=0.0,logSd=1.0)
                  PositiveReal rateCG ~ LogNormal(logMean=0.0,logSd=1.0)
                  PositiveReal rateCT ~ LogNormal(logMean=0.0,logSd=1.0)
                  QMatrix q = gtr(rateAC=rateAC,rateAG=rateAG,rateAT=rateAT,rateCG=rateCG,rateCT=rateCT,rateGT=1.0,baseFrequencies=pi)
                  PositiveReal gamma ~ LogNormal(logMean=0.0,logSd=2.0)
                  Vector<Rate> siteRates ~ DiscreteGammaInv(shape=gamma,numCategories=4,numSites=numSites(cats))
                  PositiveReal sigma ~ LogNormal(logMean=-0.693,logSd=0.5)
                  Rate mRate ~ LogNormal(logMean=-9.0,logSd=5.0)
                  Vector<Rate> branchRates ~ IID(base=LogNormal(mean=1.0,logSd=sigma),num=36)
                  Vector<Rate> clock = sharedRatesClock(tree=tree,rates=branchRates,meanRate=mRate,normalize=false)
                  Alignment<Character> sequence ~ PhyloCTMC(tree=tree,qMatrix=q,siteRates=siteRates,
                    branchRates=clock,useAmbiguities=true) observed as cats
                }
                mcmc {
                  Integer chainLength=200
                  Integer defaultLogEvery=10
                  Integer randomSeed=12345
                }
                """.formatted(String.join("\n", clades), String.join(",", names), rho,
                joint ? "joint" : "independentUniform", conditioned, tree.getRoot().toNewick() + ";");
    }

    private static void writeAlignment(Alignment alignment, Path path) throws Exception {
        StringBuilder nexus = new StringBuilder("#NEXUS\nbegin data;\ndimensions ntax=")
                .append(alignment.getTaxonCount()).append(" nchar=").append(alignment.getSiteCount())
                .append(";\nformat datatype=dna missing=? gap=-;\nmatrix\n");
        for (String taxon : alignment.getTaxaNames()) {
            nexus.append("'").append(taxon.replace("'", "''")).append("' ")
                    .append(alignment.getSequenceAsString(taxon)).append('\n');
        }
        Files.writeString(path, nexus.append(";\nend;\n").toString());
    }

    private static void addClade(List<String> declarations, List<String> names, List<String> taxa,
            double lower, double upper, double confidence) {
        String name = "clade" + names.size();
        names.add(name);
        declarations.add("CalibrationClade " + name + " = calibrationClade(taxa=["
                + String.join(",", taxa.stream().map(t -> "\"" + t + "\"").toList())
                + "],lower=" + lower + ",upper=" + upper + ",confidenceLevel=" + confidence + ")");
    }

    private static Map<String, Node> branches(Tree tree) {
        Map<String, Node> result = new TreeMap<>();
        for (Node node : tree.getNodesAsArray()) if (!node.isRoot()) {
            var taxa = node.getAllLeafNodes().stream().map(Node::getID).sorted().toList();
            String key = node.isLeaf() ? node.getID() : String.join(",", taxa);
            require(result.put(key, node) == null, "Duplicate branch: " + key);
        }
        return result;
    }

    private static BEASTState evaluate(String source, Path output) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, LIBRARIES);
        var lexer = new Lexer(source);
        lexer.registerEventListener(e -> { throw new IllegalArgumentException(e.toStdOutString(source)); });
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(e -> { throw new IllegalArgumentException(e.toStdOutString(source)); });
        var statements = new EvaluateScalarFunctions().transform(new EvaluateLiterals().transform(
                new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var tiles = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        tiles.getBestTiling(statements);
        var state = tiles.applyBestTiling(new BEASTState(output.resolve("comparison").toString(), output));
        catalog.configureState(state);
        state.initializeBEASTObjects();
        return state;
    }

    private static void equal(String label, double expected, double actual, double tolerance) {
        require(Double.isFinite(expected) && Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                label + " differs: reference=" + expected + ", PhyloSpec=" + actual);
        if (!label.startsWith("clock ") && !label.startsWith("branch length ")) {
            System.out.printf("PASS %s: reference=%.12f PhyloSpec=%.12f%n", label, expected, actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
