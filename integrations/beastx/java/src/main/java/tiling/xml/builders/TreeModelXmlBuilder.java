package tiling.xml.builders;

import dr.evolution.util.Date;
import dr.evolution.util.Taxon;
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

    private XmlElement startingTreeDefinition(TreeModel treeModel) {
        boolean hasDatedTips =
                hasDatedTips(treeModel);

        return XmlElement.element("newick")
                .withId(startingTreeId(treeModel))
                .withAttribute("units", "years")
                .withAttribute("usingDates", Boolean.toString(hasDatedTips))
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