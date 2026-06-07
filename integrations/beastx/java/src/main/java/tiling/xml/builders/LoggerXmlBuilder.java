package tiling.xml.builders;

import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import tiling.BeastXState;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LoggerXmlBuilder {

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
                            getLoggedParameters(state, spec.parameterNames)
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
                            getLoggedParameters(state, spec.parameterNames)
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
            List<Parameter> parameters
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

        for (Parameter parameter : parameters) {
            logger =
                    logger.withChild(parameterReference(parameter));
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

    private List<Parameter> getLoggedParameters(
            BeastXState state,
            List<String> parameterNames
    ) {
        List<Parameter> parameters =
                new ArrayList<>();

        if (parameterNames == null) {
            parameters.addAll(state.stateNodes.keySet());
        } else {
            for (String parameterName : parameterNames) {
                Parameter parameter =
                        state.stateNodesByPhyloSpecName.get(parameterName);

                if (parameter == null) {
                    throw new IllegalArgumentException(
                            "No BEAST X state node named '" + parameterName + "' exists for XML logger."
                    );
                }

                parameters.add(parameter);
            }
        }

        parameters.sort(Comparator.comparing(LoggerXmlBuilder::parameterId));

        return parameters;
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