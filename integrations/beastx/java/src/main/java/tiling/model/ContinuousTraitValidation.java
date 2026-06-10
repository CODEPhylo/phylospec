package tiling.model;

import dr.evolution.alignment.Alignment;
import dr.evolution.continuous.Continuous;
import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;

public final class ContinuousTraitValidation {

    public static final String TRAIT_ATTRIBUTE = "continuousTrait";

    private ContinuousTraitValidation() {
    }

    public static void validateObservedTraits(
            String modelName,
            Alignment observedTraits,
            TreeModel treeModel
    ) {
        if (observedTraits == null) {
            throw new IllegalArgumentException(modelName + " requires observed continuous trait data.");
        }

        if (treeModel == null) {
            throw new IllegalArgumentException(modelName + " requires a tree model.");
        }

        int sequenceCount =
                observedTraits.getSequenceCount();

        if (sequenceCount == 0) {
            throw new IllegalArgumentException(
                    modelName + " requires at least one observed continuous trait value."
            );
        }

        for (int i = 0; i < sequenceCount; i++) {
            Taxon taxon =
                    observedTraits.getTaxon(i);

            if (taxon == null || taxon.getId() == null || taxon.getId().isBlank()) {
                throw new IllegalArgumentException(
                        modelName + " observed continuous trait data contains a taxon with no id."
                );
            }

            externalNodeForTaxon(modelName, treeModel, taxon.getId());

            double value =
                    readTraitValue(modelName, observedTraits, i);

            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        modelName + " continuous trait value for taxon '"
                                + taxon.getId()
                                + "' must be finite."
                );
            }
        }
    }

    public static void requireSingleTraitParameter(
            String modelName,
            Parameter parameter,
            String argumentName
    ) {
        if (parameter == null) {
            return;
        }

        if (parameter.getDimension() != 1) {
            throw new IllegalArgumentException(
                    modelName
                            + " currently supports one continuous trait in the BEAST X backend, but argument '"
                            + argumentName
                            + "' has dimension "
                            + parameter.getDimension()
                            + "."
            );
        }
    }

    public static double readTraitValue(
            String modelName,
            Alignment observedTraits,
            int sequenceIndex
    ) {
        Taxon taxon =
                observedTraits.getTaxon(sequenceIndex);

        Object value =
                observedTraits.getSequenceAttribute(sequenceIndex, TRAIT_ATTRIBUTE);

        if (value == null && taxon != null) {
            value = taxon.getAttribute(TRAIT_ATTRIBUTE);
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof Continuous continuous) {
            return continuous.getValue();
        }

        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        modelName
                                + " continuous trait value for taxon '"
                                + taxonId(taxon)
                                + "' must be numeric, but was '"
                                + string
                                + "'.",
                        exception
                );
            }
        }

        throw new IllegalArgumentException(
                modelName
                        + " could not find a numeric continuous trait value for taxon '"
                        + taxonId(taxon)
                        + "'."
        );
    }

    public static NodeRef externalNodeForTaxon(
            String modelName,
            TreeModel treeModel,
            String taxonId
    ) {
        for (int i = 0; i < treeModel.getExternalNodeCount(); i++) {
            NodeRef node =
                    treeModel.getExternalNode(i);

            Taxon nodeTaxon =
                    treeModel.getNodeTaxon(node);

            if (nodeTaxon != null && taxonId.equals(nodeTaxon.getId())) {
                return node;
            }
        }

        throw new IllegalArgumentException(
                modelName
                        + " observed continuous trait taxon '"
                        + taxonId
                        + "' is not present as a tree tip."
        );
    }

    private static String taxonId(Taxon taxon) {
        if (taxon == null || taxon.getId() == null) {
            return "<unknown>";
        }

        return taxon.getId();
    }
}
