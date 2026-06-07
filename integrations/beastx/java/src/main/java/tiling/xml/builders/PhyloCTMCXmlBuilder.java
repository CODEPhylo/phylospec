package tiling.xml.builders;

import dr.evomodel.branchmodel.HomogeneousBranchModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evolution.datatype.Codons;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.xml.XmlElement;
import tiling.xml.XmlPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Writes XML for PhyloCTMC likelihood components.
 *
 * Connects the alignment, tree, substitution model, site model, and
 * branch-rate model in the BEAST X XML format.
 */
public class PhyloCTMCXmlBuilder {
    private final AlignmentXmlBuilder alignmentXmlBuilder =
            new AlignmentXmlBuilder();

    private final SubstitutionModelXmlBuilder substitutionModelXmlBuilder =
            new SubstitutionModelXmlBuilder();

    private final SiteModelXmlBuilder siteModelXmlBuilder =
            new SiteModelXmlBuilder();

    private final TreeLikelihoodXmlBuilder treeLikelihoodXmlBuilder =
            new TreeLikelihoodXmlBuilder();

    private final BranchRateModelXmlBuilder branchRateModelXmlBuilder =
            new BranchRateModelXmlBuilder();

    public XmlPlan buildComponentLayer(BeastXState state) {
        XmlPlan plan =
                new XmlPlan();

        addComponents(plan, state);

        return plan;
    }

    public void addComponents(
            XmlPlan plan,
            BeastXState state
    ) {
        addDataDefinitions(plan, state);
        addSubstitutionModels(plan, state);
        addSiteRateModels(plan, state);
        addTreeLikelihoods(plan, state);
    }

    private void addDataDefinitions(
            XmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                phyloCTMCLikelihoodSpecs(state);

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            String alignmentId =
                    likelihoodId(spec) + "_alignment";

            String patternsId =
                    likelihoodId(spec) + "_patterns";

            List<XmlElement> elements =
                    alignmentXmlBuilder.buildAlignmentAndPatterns(
                            spec.getObservedAlignment(),
                            alignmentId,
                            patternsId
                    );

            plan.add(
                    XmlPlan.Section.ALIGNMENTS,
                    elements.get(0)
            );

            plan.add(
                    XmlPlan.Section.PATTERN_LISTS,
                    elements.get(1)
            );
        }
    }

    private void addSubstitutionModels(
            XmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                phyloCTMCLikelihoodSpecs(state);

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            SubstitutionModel substitutionModel =
                    homogeneousSubstitutionModel(spec);

            String substitutionModelId =
                    likelihoodId(spec) + "_substitutionModel";

            plan.addAll(
                    XmlPlan.Section.SUBSTITUTION_SITE_MODELS,
                    substitutionModelXmlBuilder.buildSubstitutionModel(
                            substitutionModel,
                            substitutionModelId
                    )
            );
        }
    }

    private void addSiteRateModels(
            XmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                phyloCTMCLikelihoodSpecs(state);

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            String likelihoodId =
                    likelihoodId(spec);

            SubstitutionModel substitutionModel =
                    homogeneousSubstitutionModel(spec);

            String substitutionModelId =
                    likelihoodId + "_substitutionModel";

            String substitutionModelTag =
                    substitutionModelXmlBuilder.substitutionModelTag(substitutionModel);

            String siteRateModelId =
                    likelihoodId + "_siteRateModel";

            plan.add(
                    XmlPlan.Section.SUBSTITUTION_SITE_MODELS,
                    siteModelXmlBuilder.buildSiteRateModel(
                            spec.getSiteRateModel(),
                            siteRateModelId,
                            substitutionModelTag,
                            substitutionModelId
                    )
            );
        }
    }

    public void validateExportBoundary(BeastXState state) {
        for (dr.inference.model.Likelihood likelihood : state.likelihoodDistributions) {
            if (!(likelihood instanceof BeastXPhyloCTMCLikelihoodSpec spec)) {
                throw unsupported(
                        "Only PhyloCTMC likelihood XML export is supported at this stage."
                );
            }

            validatePhyloCTMCLikelihoodExportBoundary(spec);
        }
    }

    private void validatePhyloCTMCLikelihoodExportBoundary(
            BeastXPhyloCTMCLikelihoodSpec spec
    ) {
        SubstitutionModel substitutionModel =
                homogeneousSubstitutionModel(spec);

        if (substitutionModel instanceof GY94CodonModel) {
            throw unsupported(
                    "Full GY94 codon PhyloCTMC XML export is not supported yet because BEAST X XML SequenceParser cannot materialize codon alignments from plain sequence text."
            );
        }

        if (spec.getObservedAlignment().getDataType() instanceof Codons) {
            throw unsupported(
                    "Full codon PhyloCTMC XML export is not supported yet because BEAST X XML SequenceParser cannot materialize codon alignments from plain sequence text."
            );
        }
    }

    private void addTreeLikelihoods(
            XmlPlan plan,
            BeastXState state
    ) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                phyloCTMCLikelihoodSpecs(state);

        for (BeastXPhyloCTMCLikelihoodSpec spec : likelihoodSpecs) {
            String likelihoodId =
                    likelihoodId(spec);

            String patternsId =
                    likelihoodId + "_patterns";

            TreeModel treeModel =
                    spec.getTreeModel();

            String treeModelId =
                    treeModel.getId();

            if (treeModelId == null || treeModelId.isBlank()) {
                throw new IllegalArgumentException(
                        "Cannot serialize PhyloCTMC treeLikelihood without a named tree model."
                );
            }

            String siteRateModelId =
                    likelihoodId + "_siteRateModel";

            XmlElement branchRateModelReference =
                    branchRateModelReferenceForTree(
                            state,
                            treeModel
                    );

            plan.add(
                    XmlPlan.Section.TREE_LIKELIHOODS,
                    treeLikelihoodXmlBuilder.buildTreeLikelihood(
                            likelihoodId,
                            patternsId,
                            treeModelId,
                            siteRateModelId,
                            branchRateModelReference
                    )
            );

            plan.add(
                    XmlPlan.Section.MCMC_LIKELIHOOD,
                    treeLikelihoodXmlBuilder.treeLikelihoodReference(likelihoodId)
            );
        }
    }

    private List<BeastXPhyloCTMCLikelihoodSpec> phyloCTMCLikelihoodSpecs(BeastXState state) {
        List<BeastXPhyloCTMCLikelihoodSpec> likelihoodSpecs =
                new ArrayList<>();

        for (dr.inference.model.Likelihood likelihood : state.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec spec) {
                likelihoodSpecs.add(spec);
            }
        }

        likelihoodSpecs.sort(Comparator.comparing(PhyloCTMCXmlBuilder::likelihoodId));

        return likelihoodSpecs;
    }

    private XmlElement branchRateModelReferenceForTree(
            BeastXState state,
            TreeModel treeModel
    ) {
        BeastXState.RelaxedClockSpec relaxedClockSpec =
                state.treeRelaxedClockModels.get(treeModel);

        if (relaxedClockSpec != null) {
            return XmlElement.ref(
                    "discretizedBranchRates",
                    branchRateModelXmlBuilder.relaxedClockBranchRateModelId(treeModel, relaxedClockSpec)
            );
        }

        if (state.treeClockRateParameters.containsKey(treeModel)) {
            return XmlElement.ref(
                    "strictClockBranchRates",
                    treeId(treeModel) + "_strictClockBranchRates"
            );
        }

        String treeModelId =
                treeId(treeModel);

        for (Map.Entry<TreeModel, BeastXState.RelaxedClockSpec> entry : state.treeRelaxedClockModels.entrySet()) {
            TreeModel registeredTreeModel =
                    entry.getKey();

            if (treeModelId.equals(treeId(registeredTreeModel))) {
                return XmlElement.ref(
                        "discretizedBranchRates",
                        branchRateModelXmlBuilder.relaxedClockBranchRateModelId(
                                registeredTreeModel,
                                entry.getValue()
                        )
                );
            }
        }

        for (TreeModel registeredTreeModel : state.treeClockRateParameters.keySet()) {
            if (treeModelId.equals(treeId(registeredTreeModel))) {
                return XmlElement.ref(
                        "strictClockBranchRates",
                        treeModelId + "_strictClockBranchRates"
                );
            }
        }

        return null;
    }

    private SubstitutionModel homogeneousSubstitutionModel(
            BeastXPhyloCTMCLikelihoodSpec spec
    ) {
        if (!(spec.getBranchModel() instanceof HomogeneousBranchModel branchModel)) {
            throw unsupported(
                    "Only homogeneous branch substitution models are supported for PhyloCTMC XML export at this stage."
            );
        }

        return branchModel.getRootSubstitutionModel();
    }

    private static String likelihoodId(dr.inference.model.Likelihood likelihood) {
        String id =
                likelihood.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize unnamed BEAST X likelihood."
            );
        }

        return id;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree model.");
        }

        return id;
    }

    private static RuntimeException unsupported(String message) {
        return new UnsupportedOperationException(
                message + " Extend PhyloCTMCXmlBuilder before exporting this PhyloCTMC model to XML."
        );
    }
}