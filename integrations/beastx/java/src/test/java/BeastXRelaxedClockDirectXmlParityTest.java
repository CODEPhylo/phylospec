import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.treelikelihood.BeagleTreeLikelihood;
import dr.inference.markovchain.MarkovChain;
import dr.inference.mcmc.MCMC;
import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.OperatorSchedule;
import dr.inference.operators.RandomWalkIntegerOperator;
import dr.inference.operators.UniformIntegerOperator;
import dr.math.MathUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.model.StartingTreeSpec;
import tiling.operators.OperatorBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that the in-memory and XML-parser construction paths represent the
 * same relaxed-clock state before either path starts an MCMC chain.
 */
public class BeastXRelaxedClockDirectXmlParityTest {

    private static final double RATE_TOLERANCE = 1.0e-12;
    private static final double LIKELIHOOD_TOLERANCE = 1.0e-8;

    @TempDir
    Path temporaryDirectory;

    @Test
    public void fixedStateHasTheSameBranchRatesAndLikelihood() throws Exception {
        ParityModels models =
                buildParityModels();

        assertEquivalentTreeState(
                models.directLikelihood(),
                models.xmlLikelihood()
        );

        assertEquals(
                models.directModel().prior.getLogLikelihood(),
                prior(models.xmlMcmc()).getLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "Direct and XML prior values differ for the same state."
        );

        assertEquals(
                models.directModel().likelihood.getLogLikelihood(),
                dataLikelihood(models.xmlMcmc()).getLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "Direct and XML data likelihood values differ for the same state."
        );

        assertEquals(
                models.directModel().posterior.getLogLikelihood(),
                models.xmlMcmc().getLikelihood().getLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "Direct and XML posterior values differ for the same state."
        );
    }

    @Test
    public void matchingCategoryProposalAndRejectPreserveParity() throws Exception {
        ParityModels models =
                buildParityModels();

        RandomWalkIntegerOperator directOperator =
                relaxedClockCategoryOperator(models.directMcmc());

        RandomWalkIntegerOperator xmlOperator =
                relaxedClockCategoryOperator(models.xmlMcmc());

        assertArrayEquals(
                directOperator.getParameter().getParameterValues(),
                xmlOperator.getParameter().getParameterValues(),
                0.0,
                "Direct and XML category parameters must start identically."
        );

        ProposalResult directProposal =
                proposeAndRestore(
                        models.directMcmc(),
                        directOperator,
                        models.directLikelihood()
                );

        ProposalResult xmlProposal =
                proposeAndRestore(
                        models.xmlMcmc(),
                        xmlOperator,
                        models.xmlLikelihood()
                );

        assertArrayEquals(
                directProposal.proposedCategories(),
                xmlProposal.proposedCategories(),
                0.0,
                "The same random seed must produce the same category proposal."
        );

        assertEquals(
                directProposal.logHastingsRatio(),
                xmlProposal.logHastingsRatio(),
                0.0,
                "Direct and XML category proposals have different Hastings ratios."
        );

        assertMapEquals(
                directProposal.proposedBranchRates(),
                xmlProposal.proposedBranchRates(),
                RATE_TOLERANCE,
                "Branch rates differ after the same category proposal."
        );

        assertEquals(
                directProposal.proposedLogLikelihood(),
                xmlProposal.proposedLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "Likelihood differs after the same category proposal."
        );

        assertEquals(
                directProposal.initialLogLikelihood(),
                directProposal.restoredLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "Direct likelihood was not restored after rejection."
        );

        assertEquals(
                xmlProposal.initialLogLikelihood(),
                xmlProposal.restoredLogLikelihood(),
                LIKELIHOOD_TOLERANCE,
                "XML likelihood was not restored after rejection."
        );
    }

    @Test
    public void uniformCategoryOperatorUsesTheSameBounds() throws Exception {
        MathUtils.setSeed(1234);

        BeastXState state =
                new PhyloSpecRunner(source())
                        .buildState("relaxedClockUniformBounds");

        Parameter directParameter =
                state.treeRelaxedClockModels
                        .values()
                        .stream()
                        .findFirst()
                        .orElseThrow()
                        .rateCategoriesParameter();

        UniformIntegerOperator directOperator =
                relaxedClockUniformCategoryOperator(
                        new OperatorBuilder().build(state)
                );

        Parameter referenceParameter =
                new Parameter.Default(
                        directParameter.getParameterValues()
                );

        int lower =
                (int) Math.ceil(
                        directParameter
                                .getBounds()
                                .getLowerLimit(0)
                );

        int upper =
                (int) Math.floor(
                        directParameter
                                .getBounds()
                                .getUpperLimit(0)
                );

        referenceParameter.addBounds(
                new Parameter.DefaultBounds(
                        upper,
                        lower,
                        referenceParameter.getDimension()
                )
        );

        UniformIntegerOperator referenceOperator =
                new UniformIntegerOperator(
                        referenceParameter,
                        lower,
                        upper,
                        10.0,
                        1
                );

        for (long seed = 1; seed <= 20; seed++) {
            resetCategories(directParameter);
            resetCategories(referenceParameter);

            MathUtils.setSeed(seed);
            directOperator.operate();
            directOperator.accept(0.0);

            MathUtils.setSeed(seed);
            referenceOperator.operate();
            referenceOperator.accept(0.0);

            assertArrayEquals(
                    referenceParameter.getParameterValues(),
                    directParameter.getParameterValues(),
                    0.0,
                    "Direct and bounded-reference uniform category proposals differ "
                            + "for seed " + seed + "."
            );
        }
    }

    private ParityModels buildParityModels() throws Exception {
        MathUtils.setSeed(1234);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source());

        BeastXState state =
                runner.buildState("relaxedClockParity");

        TreeModel directTree =
                state.treeRelaxedClockModels
                        .keySet()
                        .stream()
                        .findFirst()
                        .orElseThrow();

        state.startingTreeSpecs.put(
                directTree,
                StartingTreeSpec.fixedNewick()
        );

        BeastXModel exportModel =
                runner.buildModel(state);

        Path xmlPath =
                temporaryDirectory.resolve("relaxed-clock-parity.xml");

        runner.writeXml(exportModel, xmlPath);

        BeastXModel directModel;

        try {
            directModel =
                    runner.buildMaterializedModel(state);
        } catch (RuntimeException error) {
            abortWithoutBeagle(error);
            throw error;
        }

        MCMC directMcmc =
                runner.buildMCMC(directModel, 1);

        MCMC xmlMcmc;

        try {
            xmlMcmc =
                    runner.parseXmlMCMC(xmlPath);
        } catch (RuntimeException error) {
            abortWithoutBeagle(error);
            throw error;
        }

        BeagleTreeLikelihood directLikelihood =
                findTreeLikelihood(directModel.likelihood);

        BeagleTreeLikelihood xmlLikelihood =
                findTreeLikelihood(xmlMcmc.getLikelihood());

        return new ParityModels(
                directModel,
                directMcmc,
                directLikelihood,
                xmlMcmc,
                xmlLikelihood
        );
    }

    private ProposalResult proposeAndRestore(
            MCMC mcmc,
            RandomWalkIntegerOperator operator,
            BeagleTreeLikelihood treeLikelihood
    ) {
        Likelihood posterior =
                mcmc.getLikelihood();

        Model model =
                posterior.getModel();

        posterior.makeDirty();

        double initialLogLikelihood =
                posterior.getLogLikelihood();

        model.storeModelState();
        MathUtils.setSeed(987654321);

        double logHastingsRatio =
                operator.operate();

        posterior.makeDirty();

        double proposedLogLikelihood =
                posterior.getLogLikelihood();

        double[] proposedCategories =
                operator.getParameter().getParameterValues().clone();

        Map<String, Double> proposedBranchRates =
                branchRatesByClade(treeLikelihood);

        operator.reject();
        model.restoreModelState();
        posterior.makeDirty();

        double restoredLogLikelihood =
                posterior.getLogLikelihood();

        return new ProposalResult(
                initialLogLikelihood,
                proposedLogLikelihood,
                restoredLogLikelihood,
                logHastingsRatio,
                proposedCategories,
                proposedBranchRates
        );
    }

    private void resetCategories(Parameter parameter) {
        for (int i = 0; i < parameter.getDimension(); i++) {
            parameter.setParameterValueQuietly(i, i);
        }

        parameter.setParameterValueNotifyChangedAll(
                0,
                0.0
        );
    }

    private void assertEquivalentTreeState(
            BeagleTreeLikelihood directLikelihood,
            BeagleTreeLikelihood xmlLikelihood
    ) {
        assertMapEquals(
                nodeHeightsByClade(directLikelihood.getTreeModel()),
                nodeHeightsByClade(xmlLikelihood.getTreeModel()),
                RATE_TOLERANCE,
                "Direct and XML tree heights differ before MCMC."
        );

        assertMapEquals(
                branchRatesByClade(directLikelihood),
                branchRatesByClade(xmlLikelihood),
                RATE_TOLERANCE,
                "Direct and XML branch rates differ before MCMC."
        );
    }

    private void assertMapEquals(
            Map<String, Double> expected,
            Map<String, Double> actual,
            double tolerance,
            String message
    ) {
        assertEquals(
                expected.keySet(),
                actual.keySet(),
                message + " Clade sets differ."
        );

        for (String clade : expected.keySet()) {
            assertEquals(
                    expected.get(clade),
                    actual.get(clade),
                    tolerance,
                    message + " Clade: " + clade
            );
        }
    }

    private Map<String, Double> branchRatesByClade(
            BeagleTreeLikelihood likelihood
    ) {
        Tree tree =
                likelihood.getTreeModel();

        BranchRateModel branchRateModel =
                likelihood.getBranchRateModel();

        Map<String, Double> rates =
                new LinkedHashMap<>();

        for (int i = 0; i < tree.getNodeCount(); i++) {
            NodeRef node =
                    tree.getNode(i);

            if (!tree.isRoot(node)) {
                rates.put(
                        clade(tree, node),
                        branchRateModel.getBranchRate(tree, node)
                );
            }
        }

        return rates;
    }

    private Map<String, Double> nodeHeightsByClade(Tree tree) {
        Map<String, Double> heights =
                new LinkedHashMap<>();

        for (int i = 0; i < tree.getNodeCount(); i++) {
            NodeRef node =
                    tree.getNode(i);

            heights.put(
                    clade(tree, node),
                    tree.getNodeHeight(node)
            );
        }

        return heights;
    }

    private String clade(Tree tree, NodeRef node) {
        if (tree.isExternal(node)) {
            return tree.getNodeTaxon(node).getId();
        }

        List<String> taxa =
                new ArrayList<>();

        collectTaxa(tree, node, taxa);
        Collections.sort(taxa);

        return String.join(",", taxa);
    }

    private void collectTaxa(
            Tree tree,
            NodeRef node,
            List<String> taxa
    ) {
        if (tree.isExternal(node)) {
            taxa.add(tree.getNodeTaxon(node).getId());
            return;
        }

        for (int i = 0; i < tree.getChildCount(node); i++) {
            collectTaxa(
                    tree,
                    tree.getChild(node, i),
                    taxa
            );
        }
    }

    private RandomWalkIntegerOperator relaxedClockCategoryOperator(
            MCMC mcmc
    ) {
        OperatorSchedule schedule =
                mcmc.getOperatorSchedule();

        for (int i = 0; i < schedule.getOperatorCount(); i++) {
            MCMCOperator operator =
                    schedule.getOperator(i);

            if (operator instanceof RandomWalkIntegerOperator randomWalk
                    && "branchRateCategories".equals(
                            randomWalk.getParameter().getId()
                    )) {
                return randomWalk;
            }
        }

        throw new AssertionError(
                "Could not find branchRateCategories RandomWalkIntegerOperator."
        );
    }

    private UniformIntegerOperator relaxedClockUniformCategoryOperator(
            MCMC mcmc
    ) {
        List<MCMCOperator> operators =
                new ArrayList<>();

        OperatorSchedule schedule =
                mcmc.getOperatorSchedule();

        for (int i = 0; i < schedule.getOperatorCount(); i++) {
            operators.add(schedule.getOperator(i));
        }

        return relaxedClockUniformCategoryOperator(operators);
    }

    private UniformIntegerOperator relaxedClockUniformCategoryOperator(
            List<MCMCOperator> operators
    ) {
        for (MCMCOperator operator : operators) {

            if (operator instanceof UniformIntegerOperator uniform
                    && "branchRateCategories".equals(
                            uniform.getParameter().getId()
                    )) {
                return uniform;
            }
        }

        throw new AssertionError(
                "Could not find branchRateCategories UniformIntegerOperator."
        );
    }

    private CompoundLikelihood prior(MCMC mcmc) {
        return compoundChild(mcmc, "prior");
    }

    private CompoundLikelihood dataLikelihood(MCMC mcmc) {
        return compoundChild(mcmc, "likelihood");
    }

    private CompoundLikelihood compoundChild(
            MCMC mcmc,
            String id
    ) {
        CompoundLikelihood posterior =
                assertInstanceOf(
                        CompoundLikelihood.class,
                        mcmc.getLikelihood()
                );

        for (Likelihood likelihood : posterior.getLikelihoods()) {
            if (id.equals(likelihood.getId())) {
                return assertInstanceOf(
                        CompoundLikelihood.class,
                        likelihood
                );
            }
        }

        throw new AssertionError(
                "Could not find compound likelihood: " + id
        );
    }

    private BeagleTreeLikelihood findTreeLikelihood(
            Likelihood likelihood
    ) {
        if (likelihood instanceof BeagleTreeLikelihood treeLikelihood) {
            return treeLikelihood;
        }

        if (likelihood instanceof CompoundLikelihood compoundLikelihood) {
            for (Likelihood child : compoundLikelihood.getLikelihoods()) {
                BeagleTreeLikelihood found =
                        findTreeLikelihoodOrNull(child);

                if (found != null) {
                    return found;
                }
            }
        }

        throw new AssertionError(
                "Could not find a BeagleTreeLikelihood in " + likelihood
        );
    }

    private BeagleTreeLikelihood findTreeLikelihoodOrNull(
            Likelihood likelihood
    ) {
        if (likelihood instanceof BeagleTreeLikelihood treeLikelihood) {
            return treeLikelihood;
        }

        if (likelihood instanceof CompoundLikelihood compoundLikelihood) {
            for (Likelihood child : compoundLikelihood.getLikelihoods()) {
                BeagleTreeLikelihood found =
                        findTreeLikelihoodOrNull(child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private void abortWithoutBeagle(RuntimeException error) {
        String message =
                error.getMessage();

        if (message != null
                && (
                message.contains(
                        "No acceptable BEAGLE library plugins found"
                )
                        || message.contains(
                        "Native BEAGLE is required"
                )
        )) {
            Assumptions.abort(
                    "Skipping relaxed-clock parity test because "
                            + "the BEAGLE native library is unavailable."
            );
        }
    }

    private String source() {
        return """
                Alignment data = fromNexus(
                    file="src/test/java/resources/primate-mtDNA.nex"
                )

                Taxa taxa = taxa(data)

                PositiveReal kappa ~ LogNormal(
                    logMean=1.0,
                    logSd=0.5
                )

                Simplex baseFrequencies ~ Dirichlet(
                    concentration=repeat(1.0, num=4)
                )

                QMatrix qMatrix = hky(
                    kappa=kappa,
                    baseFrequencies=baseFrequencies
                )

                PositiveReal populationSize ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Tree tree ~ Coalescent(
                    populationSize=constantPopulationFunction(
                        populationSize=populationSize
                    ),
                    taxa=taxa
                )

                Vector<Rate> branchRates ~ RelaxedClock(
                    clockRate=1.0,
                    base=LogNormal(
                        mean=1.0,
                        logSd=0.5
                    ),
                    tree=tree
                )

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=qMatrix,
                    branchRates=branchRates
                ) observed as data

                mcmc {
                    Integer chainLength = 1
                    Integer randomSeed = 1234

                    Logger screenLogger = screenLogger(
                        logEvery=1
                    )
                }
                """;
    }

    private record ParityModels(
            BeastXModel directModel,
            MCMC directMcmc,
            BeagleTreeLikelihood directLikelihood,
            MCMC xmlMcmc,
            BeagleTreeLikelihood xmlLikelihood
    ) {
    }

    private record ProposalResult(
            double initialLogLikelihood,
            double proposedLogLikelihood,
            double restoredLogLikelihood,
            double logHastingsRatio,
            double[] proposedCategories,
            Map<String, Double> proposedBranchRates
    ) {
    }
}
