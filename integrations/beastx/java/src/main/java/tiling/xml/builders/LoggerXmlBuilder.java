package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                            spec.fileName,
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
                            spec.fileName,
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
                    null;
        };
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
}