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
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds XML logger definitions matching the logger specs in a BEAST X state.
 */
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

        for (ScreenLoggerSpec<String> spec : state.screenLoggerSpecs) {
            loggers.add(
                    parameterLogger(
                            "screenLogger" + loggerIndex,
                            spec.logEvery(),
                            null,
                            getLoggedElements(state, spec.parameters(), spec.parameterNames())
                    )
            );

            loggerIndex++;
        }

        for (FileLoggerSpec<String> spec : state.fileLoggerSpecs) {
            loggers.add(
                    parameterLogger(
                            "fileLogger" + loggerIndex,
                            spec.logEvery(),
                            spec.fileName(),
                            getLoggedElements(state, spec.parameters(), spec.parameterNames())
                    )
            );

            loggerIndex++;
        }

        int treeLoggerIndex =
                1;

        for (TreeLoggerSpec<String> spec : state.treeLoggerSpecs) {
            loggers.add(
                    treeLogger(
                            "treeLogger" + treeLoggerIndex,
                            spec.logEvery(),
                            spec.fileName(),
                            getLoggedTree(state, spec.tree())
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
            TreeModel treeModel
    ) {
        return XmlElement.element("logTree")
                .withId(id)
                .withAttribute("logEvery", logEvery)
                .withAttribute("fileName", fileName)
                .withAttribute("overwrite", "true")
                .withAttribute("nexusFormat", "true")
                .withChild(treeReference(treeModel));
    }

    /**
     * Returns the elements logged by a screen or file logger. The posterior, prior and likelihood
     * are always logged first. Without parameters, the tree statistics and all sampled parameters
     * are logged as well.
     */
    private List<XmlElement> getLoggedElements(
            BeastXState state,
            List<String> parameterNames,
            List<String> labels
    ) {
        List<String> distributionNames = state.getXmlLoggedDistributionNames();

        List<XmlElement> loggedElements =
                new ArrayList<>();

        for (String distributionName : distributionNames) {
            loggedElements.add(getLoggedElement(state, distributionName));
        }

        if (parameterNames == null) {
            parameterNames = state.getXmlDefaultLoggedParameterNames();
        }

        for (int i = 0; i < parameterNames.size(); i++) {
            String parameterName = parameterNames.get(i);
            XmlElement loggedElement = getLoggedElement(state, parameterName);

            if (labels != null) {
                loggedElements.add(labelElement(loggedElement, labels.get(i)));
            } else if (!distributionNames.contains(getDistributionName(parameterName))) {
                loggedElements.add(loggedElement);
            }
        }

        return loggedElements;
    }

    /** Returns the name under which a distribution is always logged, resolving the alias of the posterior. */
    private static String getDistributionName(String loggableName) {
        return JOINT_LOG_NAME.equals(loggableName) ? POSTERIOR_LOG_NAME : loggableName;
    }

    private XmlElement getLoggedElement(BeastXState state, String parameterName) {
        XmlElement compoundLikelihoodReference =
                compoundLikelihoodReference(state, parameterName);

        if (compoundLikelihoodReference != null) {
            return compoundLikelihoodReference;
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

        return parameterReference(parameter);
    }

    /** Wraps a logged element into a {@code column} element that logs it under the given label. */
    private XmlElement labelElement(XmlElement loggedElement, String label) {
        return XmlElement.element("column")
                .withAttribute("label", label)
                .withChild(loggedElement);
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
                if (state.likelihoodDistributions.isEmpty() && state.observedTreeDistributions.isEmpty()) {
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

    private TreeModel getLoggedTree(
            BeastXState state,
            String treeName
    ) {
        TreeModel treeModel =
                state.treeModelsByPhyloSpecName.get(treeName);

        if (treeModel == null) {
            throw new IllegalArgumentException(
                    "No BEAST X tree model named '" + treeName + "' exists for XML tree logger."
            );
        }

        return treeModel;
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
