package tiling.xml.builders;

import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LoggerXmlBuilder {

    private static final String POSTERIOR_LOG_NAME =
            "posterior";

    private static final String JOINT_LOG_NAME =
            "joint";

    private static final String PRIOR_LOG_NAME =
            "prior";

    private static final String LIKELIHOOD_LOG_NAME =
            "likelihood";

    public List<XmlElement> buildLoggers(BeastXState state) {
        List<XmlElement> loggers =
                new ArrayList<>();

        int loggerIndex =
                1;

        for (BeastXState.ScreenLoggerSpec spec : state.screenLoggerSpecs) {
            loggers.add(
                    parameterLogger(
                            "screenLogger" + loggerIndex,
                            spec.logEvery,
                            null,
                            getLoggedElements(state, spec.parameterNames)
                    )
            );

            loggerIndex++;
        }

        for (BeastXState.FileLoggerSpec spec : state.fileLoggerSpecs) {
            loggers.add(
                    parameterLogger(
                            "fileLogger" + loggerIndex,
                            spec.logEvery,
                            xmlFileName(spec.fileName),
                            getLoggedElements(state, spec.parameterNames)
                    )
            );

            loggerIndex++;
        }

        int treeLoggerIndex =
                1;

        for (BeastXState.TreeLoggerSpec spec : state.treeLoggerSpecs) {
            loggers.add(
                    treeLogger(
                            "treeLogger" + treeLoggerIndex,
                            spec.logEvery,
                            xmlFileName(spec.fileName),
                            getLoggedTrees(state, spec.treeNames)
                    )
            );

            treeLoggerIndex++;
        }

        return loggers;
    }

    private XmlElement parameterLogger(
            String id,
            long logEvery,
            String fileName,
            List<XmlElement> loggedElements
    ) {
        XmlElement logger =
                XmlElement.element("log")
                        .withId(id)
                        .withAttribute("logEvery", logEvery);

        if (fileName != null) {
            logger =
                    logger.withAttribute("fileName", fileName)
                            .withAttribute("overwrite", "true");
        }

        for (XmlElement loggedElement : loggedElements) {
            logger =
                    logger.withChild(loggedElement);
        }

        return logger;
    }

    private XmlElement treeLogger(
            String id,
            long logEvery,
            String fileName,
            List<TreeModel> treeModels
    ) {
        XmlElement logger =
                XmlElement.element("logTree")
                        .withId(id)
                        .withAttribute("logEvery", logEvery)
                        .withAttribute("fileName", fileName)
                        .withAttribute("overwrite", "true")
                        .withAttribute("nexusFormat", "true");

        for (TreeModel treeModel : treeModels) {
            logger =
                    logger.withChild(treeReference(treeModel));
        }

        return logger;
    }

    private String xmlFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        int separatorIndex =
                Math.max(
                        fileName.lastIndexOf('/'),
                        fileName.lastIndexOf('\\')
                );

        String baseName =
                separatorIndex < 0
                        ? fileName
                        : fileName.substring(separatorIndex + 1);

        // It a little bit too hard-coded, after maybe need to change
        if (baseName.startsWith("phylo-")) {
            return baseName.substring("phylo-".length());
        }

        return baseName;
    }

    private List<XmlElement> getLoggedElements(
            BeastXState state,
            List<String> parameterNames
    ) {
        if (parameterNames == null) {
            return defaultLoggedElements(state);
        }

        List<XmlElement> loggedElements =
                new ArrayList<>();

        for (String parameterName : parameterNames) {
            XmlElement compoundLikelihoodReference =
                    compoundLikelihoodReference(state, parameterName);

            if (compoundLikelihoodReference != null) {
                loggedElements.add(compoundLikelihoodReference);
                continue;
            }

            Parameter parameter =
                    state.stateNodesByPhyloSpecName.get(parameterName);

            if (parameter == null) {
                throw new IllegalArgumentException(
                        "No BEAST X state node or XML loggable named '"
                                + parameterName
                                + "' exists for XML logger."
                );
            }

            loggedElements.add(parameterReference(parameter));
        }

        return loggedElements;
    }

    private List<XmlElement> defaultLoggedElements(BeastXState state) {
        List<Parameter> parameters =
                new ArrayList<>(state.stateNodes.keySet());

        parameters.sort(Comparator.comparing(LoggerXmlBuilder::parameterId));

        List<XmlElement> loggedElements =
                new ArrayList<>();

        for (Parameter parameter : parameters) {
            loggedElements.add(parameterReference(parameter));
        }

        return loggedElements;
    }

    private XmlElement compoundLikelihoodReference(
            BeastXState state,
            String loggableName
    ) {
        if (loggableName == null) {
            return null;
        }

        return switch (loggableName) {
            case POSTERIOR_LOG_NAME, JOINT_LOG_NAME ->
                    XmlElement.ref("joint", "joint");

            case PRIOR_LOG_NAME ->
                    XmlElement.ref("prior", "prior");

            case LIKELIHOOD_LOG_NAME -> {
                if (state.likelihoodDistributions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Cannot log 'likelihood' because this BEAST X XML model has no likelihood block."
                    );
                }

                yield XmlElement.ref("likelihood", "likelihood");
            }

            default ->
                    componentLikelihoodReference(state, loggableName);
        };
    }

    private XmlElement componentLikelihoodReference(
            BeastXState state,
            String loggableName
    ) {
        for (AbstractDistributionLikelihood prior : state.priorDistributions.values()) {
            XmlElement reference =
                    priorReference(prior, loggableName);

            if (reference != null) {
                return reference;
            }
        }

        for (AbstractDistributionLikelihood prior : state.calibrationPriorDistributions) {
            XmlElement reference =
                    priorReference(prior, loggableName);

            if (reference != null) {
                return reference;
            }
        }

        for (Map.Entry<TreeModel, AbstractModelLikelihood> entry : state.treePriorDistributions.entrySet()) {
            AbstractModelLikelihood treePrior =
                    entry.getValue();

            if (!loggableName.equals(likelihoodId(treePrior))) {
                continue;
            }

            if (treePrior instanceof CoalescentLikelihood) {
                return XmlElement.ref("coalescentLikelihood", loggableName);
            }

            if (treePrior instanceof SpeciationLikelihood) {
                return XmlElement.ref("speciationLikelihood", loggableName);
            }
        }

        for (Likelihood likelihood : state.likelihoodDistributions) {
            if (!loggableName.equals(likelihoodId(likelihood))) {
                continue;
            }

            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec) {
                return XmlElement.ref("treeLikelihood", loggableName);
            }
        }

        XmlElement treeStatisticReference =
                treeStatisticReference(state, loggableName);

        if (treeStatisticReference != null) {
            return treeStatisticReference;
        }

        return null;
    }

    private XmlElement treeStatisticReference(
            BeastXState state,
            String loggableName
    ) {
        if (loggableName.endsWith(".height")) {
            String treeName =
                    loggableName.substring(0, loggableName.length() - ".height".length());

            if (state.treeModelsByPhyloSpecName.containsKey(treeName)) {
                return XmlElement.ref("treeHeightStatistic", loggableName);
            }
        }

        if (loggableName.endsWith(".treeLength")) {
            String treeName =
                    loggableName.substring(0, loggableName.length() - ".treeLength".length());

            if (state.treeModelsByPhyloSpecName.containsKey(treeName)) {
                return XmlElement.ref("treeLengthStatistic", loggableName);
            }
        }

        return null;
    }

    private XmlElement priorReference(
            AbstractDistributionLikelihood prior,
            String loggableName
    ) {
        if (!loggableName.equals(likelihoodId(prior))) {
            return null;
        }

        if (prior instanceof DistributionLikelihood) {
            return XmlElement.ref("distributionLikelihood", loggableName);
        }

        if (prior instanceof MultivariateDistributionLikelihood) {
            return XmlElement.ref("dirichletParameterPrior", loggableName);
        }

        return null;
    }

    private List<TreeModel> getLoggedTrees(
            BeastXState state,
            List<String> treeNames
    ) {
        List<TreeModel> trees =
                new ArrayList<>();

        if (treeNames == null) {
            trees.addAll(state.treePriorDistributions.keySet());
        } else {
            for (String treeName : treeNames) {
                TreeModel treeModel =
                        state.treeModelsByPhyloSpecName.get(treeName);

                if (treeModel == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X tree model named '" + treeName + "' exists for XML tree logger."
                    );
                }

                trees.add(treeModel);
            }
        }

        trees.sort(Comparator.comparing(LoggerXmlBuilder::treeId));

        return trees;
    }

    private XmlElement parameterReference(Parameter parameter) {
        return XmlElement.ref("parameter", parameterId(parameter));
    }

    private XmlElement treeReference(TreeModel treeModel) {
        return XmlElement.ref("treeModel", treeId(treeModel));
    }

    private static String parameterId(Parameter parameter) {
        String id =
                parameter.getId();

        if (id == null || id.isBlank()) {
            id =
                    parameter.getParameterName();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X parameter.");
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

    private static String likelihoodId(Likelihood likelihood) {
        String id =
                likelihood.getId();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot serialize unnamed BEAST X likelihood.");
        }

        return id;
    }
}
