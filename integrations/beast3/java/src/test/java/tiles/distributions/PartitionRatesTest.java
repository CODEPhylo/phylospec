package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.VectorElement;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

class PartitionRatesTest {
    @TempDir
    Path directory;

    private final String previousJavaOnly = System.getProperty("java.only");

    @AfterEach
    void restoreEngineSetting() {
        if (previousJavaOnly == null) System.clearProperty("java.only");
        else System.setProperty("java.only", previousJavaOnly);
    }

    private static final String SOURCE = """
            data {
                Alignment data = fromNexus(file="tiny.nex")
                Tree tree = fromNewick(newickString="((A:0.1,B:0.1):0.1,C:0.2);")
            }
            model {
                Vector<PositiveReal> rates ~ IID(base=LogNormal(logMean=0.0,logSd=1.0),num=3)
                PositiveReal shape ~ LogNormal(logMean=0.0,logSd=1.0)
                Vector<Rate> sites ~ DiscreteGammaInv(shape=shape,numCategories=4,numSites=6)
                Alignment<Character> first ~ PhyloCTMC(tree=tree,qMatrix=jc69(),siteRates=sites,mutationRate=rates[1]) observed as data
                Alignment<Character> second ~ PhyloCTMC(tree=tree,qMatrix=hky(kappa=2.0,baseFrequencies=[0.25,0.25,0.25,0.25]),siteRates=sites,mutationRate=rates[2]) observed as data
                Alignment<Character> third ~ PhyloCTMC(tree=tree,qMatrix=jc69(),siteRates=sites,mutationRate=rates[3]) observed as data
            }
            """;

    @Test
    void partitionsOwnSiteModelsButShareVectorAndShapeThroughUpdatesAndRestore() throws Exception {
        var graph = build(SOURCE);
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(3, graph.likelihoodDistributions.size());
        var likelihoods = graph.likelihoodDistributions.stream()
                .map(TreeLikelihood.class::cast)
                .sorted(Comparator.comparing(TreeLikelihood::getID))
                .toList();
        var sites = likelihoods.stream()
                .map(l -> (SiteModel) l.siteModelInput.get())
                .toList();
        assertEquals(3, sites.stream().distinct().count());
        var rates = (RealVectorParam<?>) graph.stateNodes.keySet().stream()
                .filter(n -> n.getID().equals("rates"))
                .findFirst()
                .orElseThrow();
        for (int i = 0; i < 3; i++) {
            var view = assertInstanceOf(
                    VectorElement.class, sites.get(i).muParameterInput.get());
            assertSame(rates, view.vectorInput.get());
            assertEquals(i, view.indexInput.get());
            assertSame(
                    sites.getFirst().shapeParameterInput.get(),
                    sites.get(i).shapeParameterInput.get());
            assertSame(
                    likelihoods.getFirst().treeInput.get(),
                    likelihoods.get(i).treeInput.get());
        }
        assertNotSame(
                sites.getFirst().substModelInput.get(),
                sites.get(1).substModelInput.get());
        var factors = new ArrayList<>(graph.priorDistributions.values());
        factors.addAll(likelihoods);
        var posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        var engine = new State();
        engine.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        engine.initialise();
        engine.setPosterior(posterior);
        double before = engine.robustlyCalcPosterior(posterior);
        assertTrue(Double.isFinite(before));
        engine.store(1);
        rates.set(0, 0.8);
        rates.set(1, 1.1);
        rates.set(2, 2.1);
        engine.storeCalculationNodes();
        engine.checkCalculationNodesDirtiness();
        assertTrue(Double.isFinite(posterior.calculateLogP()));
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    nativeDensity(likelihoods.get(i), rates.get(i)),
                    likelihoods.get(i).getCurrentLogP(),
                    1e-10);
        }
        engine.restore();
        engine.restoreCalculationNodes();
        engine.setEverythingDirty(false);
        assertEquals(before, posterior.getCurrentLogP(), 1e-10);
        assertEquals(before, engine.robustlyCalcPosterior(posterior), 1e-10);
    }

    @Test
    void omissionKeepsUnitMultiplierAndInvalidRatesAreRejected() throws Exception {
        var graph = build(SOURCE.replace(",mutationRate=rates[1]", "")
                .replace(",mutationRate=rates[2]", "")
                .replace(",mutationRate=rates[3]", ""));
        for (var factor : graph.likelihoodDistributions) {
            var likelihood = (TreeLikelihood) factor;
            assertEquals(nativeDensity(likelihood, 1.0), likelihood.calculateLogP(), 1e-10);
        }
        assertThrows(Exception.class, () -> build(SOURCE.replace("mutationRate=rates[1]", "mutationRate=0.0")));
        assertThrows(Exception.class, () -> build(SOURCE.replace("mutationRate=rates[1]", "mutationRate=-1.0")));
    }

    private static double nativeDensity(TreeLikelihood actual, double rate) {
        var source = (SiteModel) actual.siteModelInput.get();
        var site = new SiteModel();
        site.initByName(
                "substModel",
                source.substModelInput.get(),
                "mutationRate",
                new RealScalarParam<>(rate, PositiveReal.INSTANCE),
                "shape",
                source.shapeParameterInput.get(),
                "gammaCategoryCount",
                4);
        var reference = new TreeLikelihood();
        reference.initByName(
                "tree",
                actual.treeInput.get(),
                "data",
                actual.dataInput.get(),
                "siteModel",
                site,
                "branchRateModel",
                actual.branchRateModelInput.get());
        return reference.calculateLogP();
    }

    private BEASTState build(String source) throws Exception {
        System.setProperty("java.only", "true");
        Files.writeString(directory.resolve("tiny.nex"), """
                #NEXUS
                Begin data;
                Dimensions ntax=3 nchar=6;
                Format datatype=dna;
                Matrix
                A ACGTAC
                B ACGTAT
                C AGGTAT
                ;
                End;
                """);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals()
                        .transform(
                                new RemoveGroupings().transform(new Parser(new Lexer(source).scanTokens()).parse())));
        var resolver = new TypeResolver(catalog.getComponentResolver());
        resolver.registerEventListener(error -> {
            throw new IllegalArgumentException(error.toStdOutString(source));
        });
        resolver.visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("partition-rates", directory));
        graph.initializeBEASTObjects();
        return graph;
    }
}
