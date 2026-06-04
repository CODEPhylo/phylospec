package tiling.xml.builders;

import tiling.xml.BeastXXmlElement;

public class BeastXTreeLikelihoodXmlBuilder {

    public BeastXXmlElement buildTreeLikelihood(
            String likelihoodId,
            String patternsId,
            String treeModelId,
            String siteRateModelId,
            String branchRateModelId
    ) {
        BeastXXmlElement treeLikelihood =
                BeastXXmlElement.element("treeLikelihood")
                        .withId(likelihoodId)
                        .withAttribute("useAmbiguities", false)
                        .withChild(
                                BeastXXmlElement.ref("patterns", patternsId)
                        )
                        .withChild(
                                BeastXXmlElement.ref("treeModel", treeModelId)
                        )
                        .withChild(
                                BeastXXmlElement.ref("siteModel", siteRateModelId)
                        );

        if (branchRateModelId != null && !branchRateModelId.isBlank()) {
            treeLikelihood =
                    treeLikelihood.withChild(
                            BeastXXmlElement.ref("strictClockBranchRates", branchRateModelId)
                    );
        }

        return treeLikelihood;
    }

    public BeastXXmlElement treeLikelihoodReference(String likelihoodId) {
        return BeastXXmlElement.ref("treeLikelihood", likelihoodId);
    }
}