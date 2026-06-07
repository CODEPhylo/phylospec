package tiling.xml.builders;

import tiling.xml.XmlElement;

public class TreeLikelihoodXmlBuilder {

    public XmlElement buildTreeLikelihood(
            String likelihoodId,
            String patternsId,
            String treeModelId,
            String siteRateModelId,
            XmlElement branchRateModelReference
    ) {
        XmlElement treeLikelihood =
                XmlElement.element("treeLikelihood")
                        .withId(likelihoodId)
                        .withAttribute("useAmbiguities", false)
                        .withChild(
                                XmlElement.ref("patterns", patternsId)
                        )
                        .withChild(
                                XmlElement.ref("treeModel", treeModelId)
                        )
                        .withChild(
                                XmlElement.ref("siteModel", siteRateModelId)
                        );

        if (branchRateModelReference != null) {
            treeLikelihood =
                    treeLikelihood.withChild(branchRateModelReference);
        }

        return treeLikelihood;
    }

    public XmlElement treeLikelihoodReference(String likelihoodId) {
        return XmlElement.ref("treeLikelihood", likelihoodId);
    }
}