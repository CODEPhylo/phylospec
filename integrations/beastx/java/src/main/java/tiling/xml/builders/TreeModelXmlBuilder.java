package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import tiling.xml.XmlElement;
import tiling.xml.XmlPlan;

import java.util.Set;

public class TreeModelXmlBuilder {

    public void addTreeDefinitions(
            XmlPlan plan,
            TreeModel treeModel,
            Set<String> emittedTaxonIds
    ) {
        addTaxonDefinitions(plan, treeModel, emittedTaxonIds);

        plan.add(
                XmlPlan.Section.STARTING_TREES,
                startingTreeDefinition(treeModel)
        );

        plan.add(
                XmlPlan.Section.TREE_MODELS,
                treeModelDefinition(treeModel)
        );
    }

    private void addTaxonDefinitions(
            XmlPlan plan,
            TreeModel treeModel,
            Set<String> emittedTaxonIds
    ) {
        for (int i = 0; i < treeModel.getTaxonCount(); i++) {
            String taxonId =
                    treeModel.getTaxonId(i);

            if (taxonId == null || taxonId.isBlank()) {
                throw new IllegalArgumentException("Cannot serialize unnamed BEAST X taxon.");
            }

            if (!emittedTaxonIds.add(taxonId)) {
                continue;
            }

            plan.add(
                    XmlPlan.Section.TAXA,
                    XmlElement.element("taxon")
                            .withId(taxonId)
            );
        }
    }

    private XmlElement startingTreeDefinition(TreeModel treeModel) {
        return XmlElement.element("newick")
                .withId(startingTreeId(treeModel))
                .withAttribute("units", "years")
                .withAttribute("usingDates", "false")
                .withAttribute("usingHeights", "false")
                .withText(ensureTrailingSemicolon(treeModel.getNewick()));
    }

    private XmlElement treeModelDefinition(TreeModel treeModel) {
        String id =
                treeId(treeModel);

        return XmlElement.element("treeModel")
                .withId(id)
                .withChild(XmlElement.ref("newick", startingTreeId(treeModel)))
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

    private static String ensureTrailingSemicolon(String newick) {
        String trimmed =
                newick.trim();

        if (trimmed.endsWith(";")) {
            return trimmed;
        }

        return trimmed + ";";
    }
}