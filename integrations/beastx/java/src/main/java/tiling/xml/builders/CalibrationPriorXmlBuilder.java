package tiling.xml.builders;

import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Statistic;
import dr.math.distributions.Distribution;
import dr.util.Attribute;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CalibrationPriorXmlBuilder {

    public boolean supports(AbstractDistributionLikelihood calibrationPrior) {
        if (!(calibrationPrior instanceof DistributionLikelihood distributionLikelihood)) {
            return false;
        }

        Distribution distribution =
                distributionLikelihood.getDistribution();

        if (!(distribution instanceof UniformDistributionModel)) {
            return false;
        }

        if (calibrationPrior.getDataList().size() != 1) {
            return false;
        }

        Attribute<double[]> data =
                calibrationPrior.getDataList().get(0);

        return data instanceof TMRCAStatistic;
    }

    public XmlElement buildStatistic(AbstractDistributionLikelihood calibrationPrior) {
        TMRCAStatistic statistic =
                calibrationStatistic(calibrationPrior);

        XmlElement element =
                XmlElement.element("tmrcaStatistic")
                        .withId(statisticId(statistic))
                        .withAttribute("name", statistic.getStatisticName())
                        .withAttribute("absolute", "false")
                        .withChild(treeReference((TreeModel) statistic.getTree()));

        Set<String> leafSet =
                statistic.getLeafSet();

        if (leafSet == null || leafSet.isEmpty()) {
            return element;
        }

        XmlElement taxa =
                XmlElement.element("taxa")
                        .withId(statisticId(statistic) + "_taxa");

        List<String> taxonIds =
                new ArrayList<>(leafSet);

        taxonIds.sort(String::compareTo);

        for (String taxonId : taxonIds) {
            taxa =
                    taxa.withChild(
                            XmlElement.ref("taxon", taxonId)
                    );
        }

        return element.withChild(
                XmlElement.element("mrca")
                        .withChild(taxa)
        );
    }

    public XmlElement buildPrior(AbstractDistributionLikelihood calibrationPrior) {
        if (!(calibrationPrior instanceof DistributionLikelihood distributionLikelihood)) {
            throw unsupported("Only DistributionLikelihood calibration priors are supported.");
        }

        Distribution distribution =
                distributionLikelihood.getDistribution();

        if (!(distribution instanceof UniformDistributionModel uniformDistribution)) {
            throw unsupported("Only uniform calibration priors are supported.");
        }

        TMRCAStatistic statistic =
                calibrationStatistic(calibrationPrior);

        String priorId =
                distributionLikelihoodId(calibrationPrior);

        return XmlElement.element("distributionLikelihood")
                .withId(priorId)
                .withChild(
                        XmlElement.element("distribution")
                                .withChild(
                                        XmlElement.element("uniformDistributionModel")
                                                .withId(priorId + "_distribution")
                                                .withChild(
                                                        XmlElement.element("lower")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_lower",
                                                                                uniformDistribution.getLower(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                                .withChild(
                                                        XmlElement.element("upper")
                                                                .withChild(
                                                                        inlineParameterDefinition(
                                                                                priorId + "_upper",
                                                                                uniformDistribution.getUpper(),
                                                                                null,
                                                                                null
                                                                        )
                                                                )
                                                )
                                )
                )
                .withChild(
                        XmlElement.element("data")
                                .withChild(
                                        XmlElement.ref("tmrcaStatistic", statisticId(statistic))
                                )
                );
    }

    public String priorId(AbstractDistributionLikelihood likelihood) {
        return distributionLikelihoodId(likelihood);
    }

    private static TMRCAStatistic calibrationStatistic(
            AbstractDistributionLikelihood calibrationPrior
    ) {
        if (calibrationPrior.getDataList().size() != 1) {
            throw unsupported("Calibration prior XML export requires exactly one statistic data element.");
        }

        Attribute<double[]> data =
                calibrationPrior.getDataList().get(0);

        if (data instanceof TMRCAStatistic statistic) {
            return statistic;
        }

        throw unsupported("Only TMRCAStatistic calibration prior data is supported.");
    }

    private XmlElement inlineParameterDefinition(
            String id,
            double value,
            Double lower,
            Double upper
    ) {
        XmlElement element =
                XmlElement.element("parameter")
                        .withId(id)
                        .withAttribute("value", format(value));

        if (lower != null) {
            element =
                    element.withAttribute("lower", format(lower));
        }

        if (upper != null) {
            element =
                    element.withAttribute("upper", format(upper));
        }

        return element;
    }

    private XmlElement treeReference(TreeModel treeModel) {
        return XmlElement.ref("treeModel", treeId(treeModel));
    }

    private static String distributionLikelihoodId(AbstractDistributionLikelihood likelihood) {
        String id =
                likelihood.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X distribution likelihood.");
        }

        return id;
    }

    private static String statisticId(Statistic statistic) {
        String id =
                statistic.getId();

        if (id == null || id.isBlank()) {
            id =
                    statistic.getStatisticName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X statistic.");
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
                message + " Extend CalibrationPriorXmlBuilder before exporting this calibration prior to XML."
        );
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot serialize NaN as a BEAST X XML number.");
        }

        if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }

        if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }

        return Double.toString(value);
    }
}
