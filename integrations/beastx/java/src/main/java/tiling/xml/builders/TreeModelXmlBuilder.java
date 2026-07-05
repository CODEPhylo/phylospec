package tiling.xml.builders;

import dr.evolution.util.Date;
import dr.evolution.util.Taxon;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.ExponentialGrowthModel;
import dr.evomodel.coalescent.demographicmodel.LogisticGrowthModel;
import dr.evomodel.coalescent.demographicmodel.PiecewisePopulationModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import tiling.BeastXState;
import tiling.model.StartingTreeSpec;
import tiling.xml.XmlElement;
import tiling.xml.XmlPlan;

import java.util.Set;

public class TreeModelXmlBuilder {

    public void addTreeDefinitions(
            XmlPlan plan,
            BeastXState state,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior,
            Set<String> emittedTaxonIds
    ) {
        addTaxonDefinitions(plan, treeModel, emittedTaxonIds);

        plan.add(
                XmlPlan.Section.STARTING_TREES,
                startingTreeDefinition(state, treeModel, treePrior)
        );

        plan.add(
                XmlPlan.Section.TREE_MODELS,
                treeModelDefinition(state, treeModel, treePrior)
        );
    }

    private void addTaxonDefinitions(
            XmlPlan plan,
            TreeModel treeModel,
            Set<String> emittedTaxonIds
    ) {
        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            Taxon taxon =
                    treeModel.getTaxon(i);

            String taxonId =
                    taxon.getId();

            if (taxonId == null || taxonId.isBlank()) {
                throw new IllegalArgumentException("Cannot serialize unnamed BEAST X taxon.");
            }

            if (!emittedTaxonIds.add(taxonId)) {
                continue;
            }

            plan.add(
                    XmlPlan.Section.TAXA,
                    taxonDefinition(taxon)
            );
        }
    }

    private XmlElement taxonDefinition(Taxon taxon) {
        XmlElement taxonElement =
                XmlElement.element("taxon")
                        .withId(taxon.getId());

        Date date =
                taxon.getDate();

        if (date == null) {
            return taxonElement;
        }

        return taxonElement.withChild(
                XmlElement.element("date")
                        .withAttribute("value", format(date.getTimeValue()))
                        .withAttribute("direction", "backwards")
                        .withAttribute("units", "years")
        );
    }

    private XmlElement startingTreeDefinition(
            BeastXState state,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        StartingTreeSpec startingTreeSpec =
                state.startingTreeSpecs.getOrDefault(
                        treeModel,
                        StartingTreeSpec.fixedNewick()
                );

        if (startingTreeSpec.type() == StartingTreeSpec.Type.COALESCENT_SIMULATOR) {
            if (!(treePrior instanceof CoalescentLikelihood coalescentLikelihood)) {
                throw new IllegalArgumentException(
                        "BEAST X coalescentSimulator starting trees require a Coalescent tree prior."
                );
            }

            return coalescentSimulatorDefinition(treeModel, coalescentLikelihood);
        }

        boolean hasDatedTips =
                hasDatedTips(treeModel);

        return XmlElement.element("newick")
                .withId(startingTreeId(treeModel))
                .withAttribute("units", "years")
                .withAttribute("usingDates", Boolean.toString(hasDatedTips))
                .withAttribute("usingHeights", "false")
                .withText(ensureTrailingSemicolon(treeModel.getNewick()));
    }

    private XmlElement coalescentSimulatorDefinition(
            TreeModel treeModel,
            CoalescentLikelihood coalescentLikelihood
    ) {
        return XmlElement.element("coalescentSimulator")
                .withId(startingTreeId(treeModel))
                .withChild(taxaContainer(treeModel))
                .withChild(
                        XmlElement.ref(
                                coalescentModelTag(coalescentLikelihood.getDemoModel()),
                                treePriorModelId(coalescentLikelihood)
                        )
                );
    }

    private XmlElement taxaContainer(TreeModel treeModel) {
        XmlElement taxa =
                XmlElement.element("taxa")
                        .withId(treeId(treeModel) + "_startingTaxa");

        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            Taxon taxon =
                    treeModel.getTaxon(i);

            taxa =
                    taxa.withChild(XmlElement.ref("taxon", taxon.getId()));
        }

        return taxa;
    }

    private XmlElement treeModelDefinition(
            BeastXState state,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        String id =
                treeId(treeModel);

        String startingTreeTag =
                startingTreeTag(state, treeModel, treePrior);

        return XmlElement.element("treeModel")
                .withId(id)
                .withChild(XmlElement.ref(startingTreeTag, startingTreeId(treeModel)))
                .withChild(
                        XmlElement.element("rootHeight")
                                .withChild(
                                        XmlElement.element("parameter")
                                                .withId(id + ".rootHeight")
                                )
                )
                .withChild(
                        XmlElement.element("nodeHeights")
                                .withAttribute("internalNodes", "true")
                                .withAttribute("rootNode", "false")
                                .withChild(
                                        XmlElement.element("parameter")
                                                .withId(id + ".internalNodeHeights")
                                )
                )
                .withChild(
                        XmlElement.element("nodeHeights")
                                .withAttribute("internalNodes", "true")
                                .withAttribute("rootNode", "true")
                                .withChild(
                                        XmlElement.element("parameter")
                                                .withId(id + ".allInternalNodeHeights")
                                )
                );
    }

    private String startingTreeTag(
            BeastXState state,
            TreeModel treeModel,
            AbstractModelLikelihood treePrior
    ) {
        StartingTreeSpec startingTreeSpec =
                state.startingTreeSpecs.getOrDefault(
                        treeModel,
                        StartingTreeSpec.fixedNewick()
                );

        if (startingTreeSpec.type() == StartingTreeSpec.Type.COALESCENT_SIMULATOR) {
            if (!(treePrior instanceof CoalescentLikelihood)) {
                throw new IllegalArgumentException(
                        "BEAST X coalescentSimulator starting trees require a Coalescent tree prior."
                );
            }

            return "coalescentSimulator";
        }

        return "newick";
    }

    private static boolean hasDatedTips(TreeModel treeModel) {
        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            Taxon taxon =
                    treeModel.getTaxon(i);

            if (taxon.getDate() != null) {
                return true;
            }
        }

        return false;
    }

    private static String treeId(TreeModel treeModel) {
        String id =
                treeModel.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree model.");
        }

        return id;
    }

    private static String startingTreeId(TreeModel treeModel) {
        return treeId(treeModel) + "_startingTree";
    }

    private static String coalescentModelTag(DemographicModel demographicModel) {
        if (demographicModel instanceof ConstantPopulationModel) {
            return "constantSize";
        }

        if (demographicModel instanceof ExponentialGrowthModel) {
            return "exponentialGrowth";
        }

        if (demographicModel instanceof LogisticGrowthModel) {
            return "logisticGrowth";
        }

        if (demographicModel instanceof PiecewisePopulationModel) {
            return "piecewisePopulation";
        }

        throw new IllegalArgumentException(
                "Only constant, exponential, logistic, and piecewise coalescent starting-tree simulators are supported."
        );
    }

    private static String treePriorModelId(AbstractModelLikelihood treePrior) {
        String id =
                treePrior.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X tree prior.");
        }

        return id + "_model";
    }

    private static String ensureTrailingSemicolon(String newick) {
        String trimmed =
                newick.trim();

        if (trimmed.endsWith(";")) {
            return trimmed;
        }

        return trimmed + ";";
    }

    private static String format(double value) {
        return Double.toString(value);
    }
}